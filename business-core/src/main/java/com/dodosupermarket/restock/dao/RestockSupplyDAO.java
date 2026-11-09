package com.dodosupermarket.restock.dao;

import com.dodosupermarket.restock.bean.po.RestockSupply;
import com.dodosupermarket.restock.bean.po.RestockSupplyKey;
import org.apache.ibatis.annotations.Param;
import pers.binaryhunter.framework.dao.GenericDAO;

/** 常规 CRUD 复用框架；锁与条件状态迁移由具名 Mapper 方法完成。 */
public interface RestockSupplyDAO extends GenericDAO<RestockSupply, RestockSupplyKey> {
    RestockSupply lockByKey(@Param("id") RestockSupplyKey id);

    int updateVersioned(@Param("record") RestockSupply record);
}
