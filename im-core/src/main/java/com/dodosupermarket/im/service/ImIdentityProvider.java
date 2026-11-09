package com.dodosupermarket.im.service;

import java.util.List;

/** 宿主应用提供可信身份及会话范围；IM 核心不依赖客户或员工 PO。 */
public interface ImIdentityProvider {
    String sessionAttribute();
    Long accountId(Object principal);
    boolean isAgent(Object principal);
    boolean canAccess(Object principal,long customer);
    List<Long> customers(Object principal);
}
