package com.dodosupermarket.restock.service.logic;

import com.dodosupermarket.restock.bean.po.RestockSupply;
import com.dodosupermarket.restock.bean.po.RestockSupplyKey;
import com.dodosupermarket.restock.dao.RestockSupplyDAO;
import com.dodosupermarket.restock.service.RestockSupplyService;
import org.springframework.stereotype.Service;
import pers.binaryhunter.framework.service.logic.GenericServiceImpl;

/** 与晒单一致，由框架承担实体 CRUD，并复用当前事务中的 MyBatis 会话。 */
@Service
public class RestockSupplyServiceImpl extends GenericServiceImpl<RestockSupply, RestockSupplyKey> implements RestockSupplyService {
    public RestockSupplyServiceImpl(RestockSupplyDAO dao) {
        this.dao = dao;
    }

    @Override
    public RestockSupply lockByKey(RestockSupplyKey id) {
        return ((RestockSupplyDAO) dao).lockByKey(id);
    }

    @Override
    public int updateVersioned(RestockSupply record) {
        return ((RestockSupplyDAO) dao).updateVersioned(record);
    }
}
