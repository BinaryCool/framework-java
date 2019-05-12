package pers.binaryhunter.db.mybatis.pulgin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import pers.binaryhunter.db.mybatis.datasource.ConnectionHolder;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.ConnectionProxy;

/**
 * 数据源读写分离路由
 */
@Intercepts({ @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}) })
public class RWPlugin implements Interceptor {
    private static final Logger logger = LoggerFactory.getLogger(RWPlugin.class);
    
	public Object intercept(Invocation invocation) throws Throwable {

		Connection conn = (Connection) invocation.getArgs()[0];
		conn = unwrapConnection(conn);
		if (conn instanceof ConnectionProxy) {			
			//强制走写库
			if(ConnectionHolder.FORCE_WRITE.get() != null && ConnectionHolder.FORCE_WRITE.get()){
				routeConnection(ConnectionHolder.WRITE, conn);
				return invocation.proceed();
			}	
			StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
			MetaObject metaObject = MetaObject.forObject(statementHandler, new DefaultObjectFactory(), new DefaultObjectWrapperFactory(), new DefaultReflectorFactory());
			MappedStatement mappedStatement = null;
			if (statementHandler instanceof RoutingStatementHandler) {
				mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
			} else {
				mappedStatement = (MappedStatement) metaObject.getValue("mappedStatement");
			}
			String key = ConnectionHolder.WRITE;

			if (mappedStatement.getSqlCommandType() == SqlCommandType.SELECT
                    && !mappedStatement.getId().endsWith("!selectKey")
                    && !mappedStatement.getId().endsWith("getSequence")
                    && !mappedStatement.getId().endsWith("getSequences")
                    ) {
				key = ConnectionHolder.READ;
			} 
			routeConnection(key, conn);
		}

		return invocation.proceed();

	}
	
	private void routeConnection(String key, Connection conn) {
		ConnectionHolder.CURRENT_CONNECTION.set(key);
        
		// 同一个线程下保证最多只有一个写数据链接和读数据链接
		if (!ConnectionHolder.CONNECTION_CONTEXT.get().containsKey(key)) {
			ConnectionProxy conToUse = (ConnectionProxy) conn;
			conn = conToUse.getTargetConnection();
			if (ConnectionHolder.READ.equals(key)) {
				try {
					conn.setAutoCommit(true); 
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
			ConnectionHolder.CONNECTION_CONTEXT.get().put(key, conn);
		} else {
            logger.debug("Connection context already contain key " + key);
        }
	}

	public Object plugin(Object target) {
		if (target instanceof StatementHandler) {  
            return Plugin.wrap(target, this);  
        } else {  
            return target;  
        }  
	}

	public void setProperties(Properties properties) {
		// NOOP

	}
    /**
     * MyBatis wraps the JDBC Connection with a logging proxy but Spring registers the original connection so it should
     * be unwrapped before calling {@code DataSourceUtils.isConnectionTransactional(Connection, DataSource)}
     * 
     * @param connection May be a {@code ConnectionLogger} proxy
     * @return the original JDBC {@code Connection}
     */
    private Connection unwrapConnection(Connection connection) {
        if (Proxy.isProxyClass(connection.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(connection);
            if (handler instanceof ConnectionLogger) {
                return ((ConnectionLogger) handler).getConnection();
            }
        }
        return connection;
    }

}
