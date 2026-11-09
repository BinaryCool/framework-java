package com.dodosupermarket.show.core;

public interface ShowMediaStore {
    /**
     * 将临时图片转为可长期访问的地址；转存失败须抛错，禁止返回原临时地址。
     * 文件操作不参与数据库回滚，调用方失败后可能需要重新上传。
     */
    String persist(String url);
}
