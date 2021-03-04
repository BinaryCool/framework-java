package pers.binaryhunter.db.mybatis.datasource;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NamedThreadLocal;
import pers.binaryhunter.db.mybatis.pulgin.RWPlugin;

public class ConnectionHolder {
    private static final Logger log = LoggerFactory.getLogger(RWPlugin.class);
    
    public static final String READ = "read";
    public static final String WRITE = "write";
    
	/**
	 * 当前数据库链接是读还是写
	 */
	public final static ThreadLocal<String> CURRENT_CONNECTION = new NamedThreadLocal<String>("routingdatasource's key"){
		protected String initialValue() {
			return WRITE;
		}
	};
	/**
	 * 当前线程所有数据库链接
	 */
	public final static ThreadLocal<Map<String, Connection>> CONNECTION_CONTEXT = new NamedThreadLocal<Map<String, Connection>>(
			"connection map"){
		protected Map<String,Connection> initialValue() {
		    return new HashMap<>();
		}
	};
	
	/**
	 * 强制写数据源
	 */
	public final static ThreadLocal<Boolean> FORCE_WRITE = new NamedThreadLocal<>("FORCE_WRITE");
}
