package com.common.system.sys.org.dao;

import com.common.system.common.base.model.Pages;
import com.common.system.sys.org.mapper.SysOrgAddressBookMapper;
import com.common.system.sys.org.model.SysOrgElement;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 地址本数据访问对象
 *
 * @author Eric
 * @date 2026/8/9
 * @description 与 SysOrgAddressBookMapper 一对一对接，不承载业务逻辑。
 */
@Repository
public class SysOrgAddressBookDao {

    private final SysOrgAddressBookMapper addressBookMapper;

    public SysOrgAddressBookDao(SysOrgAddressBookMapper addressBookMapper) {
        this.addressBookMapper = addressBookMapper;
    }

    public List<SysOrgElement> selectTree(List<Integer> orgTypes, List<String> limitRange, String parentId) {
        return addressBookMapper.selectTree(orgTypes, limitRange, parentId);
    }

    public List<SysOrgElement> selectList(String keyword, List<Integer> orgTypes, List<String> limitRange, Pages pages) {
        return addressBookMapper.selectList(keyword, orgTypes, limitRange, pages);
    }

    public long countList(String keyword, List<Integer> orgTypes, List<String> limitRange) {
        return addressBookMapper.countList(keyword, orgTypes, limitRange);
    }

    public List<SysOrgElement> selectByIds(List<String> ids) {
        return addressBookMapper.selectByIds(ids);
    }
}
