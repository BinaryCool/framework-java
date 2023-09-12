package pers.binaryhunter.db.mybatis.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NamedThreadLocal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ConnectionHolder {
    private static final Logger log = LoggerFactory.getLogger(ConnectionHolder.class);
    
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

	public static Connection getConnection(String connectionType) {
		String dataSourceName = DataSourceHolder.getCurrentDataSourceName();
		Map<String, Connection> connectionMap = ConnectionHolder.CONNECTION_CONTEXT.get();
		return connectionMap.get(dataSourceName + "_" + connectionType);
	}

	public static void rollbackConnection() throws SQLException {
		Map<String, Connection> connectionMap = ConnectionHolder.CONNECTION_CONTEXT.get();
		for (var entry : connectionMap.entrySet()) {
			if (entry.getKey().contains("_" + WRITE) && null != entry.getValue()) {
				entry.getValue().rollback();;
			}
		}
	}

	public static void commitConnection() throws SQLException {
		Map<String, Connection> connectionMap = ConnectionHolder.CONNECTION_CONTEXT.get();
		for (var entry : connectionMap.entrySet()) {
			if (entry.getKey().contains("_" + WRITE) && null != entry.getValue()) {
				entry.getValue().commit();
			}
		}
	}

	public static void closeConnection() throws SQLException {
		Map<String, Connection> connectionMap = ConnectionHolder.CONNECTION_CONTEXT.get();
		for (var connection : connectionMap.values()) {
			if (null != connection) {
				connection.close();
			}
		}
		connectionMap.clear();
	}

	public static void putConnection(String connectionType, Connection connection) {
		String dataSourceName = DataSourceHolder.getCurrentDataSourceName();
		Map<String, Connection> connectionMap = ConnectionHolder.CONNECTION_CONTEXT.get();
		connectionMap.put(dataSourceName + "_" + connectionType, connection);
	}

	public static boolean containsKey(String connectionType) {
		String dataSourceName = DataSourceHolder.getCurrentDataSourceName();
		Map<String, Connection> connectionMap = ConnectionHolder.CONNECTION_CONTEXT.get();
		return connectionMap.containsKey(dataSourceName + "_" + connectionType);
	}
}
