package com.dodosupermarket.restock.service;

import com.dodosupermarket.restock.bean.po.RestockSupply;
import com.dodosupermarket.restock.bean.po.RestockSupplyKey;
import pers.binaryhunter.framework.service.GenericService;

/** 供货记录持久化服务：常规 CRUD 由框架提供，锁定查询复用当前事务。 */
public interface RestockSupplyService extends GenericService<RestockSupply, RestockSupplyKey> {
    /** 按联合主键加行锁读取供货记录，调用方必须已开启事务。 */
    RestockSupply lockByKey(RestockSupplyKey id);

    /** 按版本号条件更新供货记录，返回 0 表示版本已变化。 */
    int updateVersioned(RestockSupply record);
}
