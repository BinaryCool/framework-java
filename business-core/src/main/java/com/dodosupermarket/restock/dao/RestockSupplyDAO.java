package com.dodosupermarket.restock.dao;

import com.dodosupermarket.restock.bean.po.RestockSupply;
import com.dodosupermarket.restock.bean.po.RestockSupplyKey;
import org.apache.ibatis.annotations.Param;
import pers.binaryhunter.framework.dao.GenericDAO;

/** 常规 CRUD 复用框架；锁与条件状态迁移由具名 Mapper 方法完成。 */
public interface RestockSupplyDAO extends GenericDAO<RestockSupply, RestockSupplyKey> {
    /** 按联合主键加行锁读取供货记录。 */
    RestockSupply lockByKey(@Param("id") RestockSupplyKey id);

    /** 按版本号条件更新供货记录，返回 0 表示版本已变化。 */
    int updateVersioned(@Param("record") RestockSupply record);
}
