package com.common.system.sys.log.service;

import com.common.system.sys.log.dao.SysLogOperationDao;
import com.common.system.sys.log.model.SysLogOperation;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 日志批量异步写入缓冲
 *
 * @author Eric
 * @date 2026/8/8
 * @description 日志只增流水、无并发写冲突，采用「内存队列 + 定时批量落库」：
 *              <ul>
 *              <li>业务线程仅做 {@link #offer(SysLogOperation)} 入队，不触碰数据库，降低日志 IO 对业务线程的影响；</li>
 *              <li>调度线程每 2 秒批量聚合写入（单条多值 INSERT），减少写库次数；</li>
 *              <li>写入失败仅记录日志，不影响主业务流程；应用关闭时 {@code @PreDestroy} 兜底 flush，尽量不丢缓冲日志。</li>
 *              </ul>
 */
@Component
public class LogWriteBuffer {

    private static final Logger log = LoggerFactory.getLogger(LogWriteBuffer.class);

    /** 单次批量写入上限，防止单条 SQL 过长 */
    private static final int BATCH_SIZE = 200;

    /** 内存队列容量上限：超出时丢弃新日志并告警，避免高并发/大流量下内存无限膨胀（日志属增强能力，可丢弃） */
    private static final int QUEUE_CAPACITY = 10000;

    private final Queue<SysLogOperation> queue = new ConcurrentLinkedQueue<>();

    private final SysLogOperationDao logDao;

    public LogWriteBuffer(SysLogOperationDao logDao) {
        this.logDao = logDao;
    }

    /**
     * 入队一条日志（非阻塞）；队列已满时丢弃并告警
     */
    public void offer(SysLogOperation entry) {
        if (null == entry) {
            return;
        }
        if (queue.size() >= QUEUE_CAPACITY) {
            log.warn("log buffer full (capacity={}), drop one log record", QUEUE_CAPACITY);
            return;
        }
        queue.offer(entry);
    }

    /**
     * 定时批量落库：每 2 秒聚合一次
     */
    @Scheduled(fixedDelay = 2000)
    public void flush() {
        List<SysLogOperation> batch = new ArrayList<>(BATCH_SIZE);
        SysLogOperation item;
        while (batch.size() < BATCH_SIZE && (item = queue.poll()) != null) {
            batch.add(item);
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            logDao.insertBatch(batch);
            if (log.isDebugEnabled()) {
                log.debug("flushed {} log records", batch.size());
            }
        } catch (Exception e) {
            // 日志写入失败不影响主业务流程，仅记录告警；失败批次丢弃（可接受：日志属增强能力）
            log.error("failed to batch insert {} log records", batch.size(), e);
        }
    }

    /**
     * 应用关闭前兜底 flush，尽量将缓冲中的日志落库
     */
    @PreDestroy
    public void flushOnShutdown() {
        flush();
    }
}
