package com.dodosupermarket.restock.service;

import com.dodosupermarket.restock.bean.po.RestockSupply;
import com.dodosupermarket.restock.bean.po.RestockSupplyKey;
import pers.binaryhunter.framework.service.GenericService;

public interface RestockSupplyService extends GenericService<RestockSupply, RestockSupplyKey> {
    RestockSupply lockByKey(RestockSupplyKey id);

    int updateVersioned(RestockSupply record);
}
