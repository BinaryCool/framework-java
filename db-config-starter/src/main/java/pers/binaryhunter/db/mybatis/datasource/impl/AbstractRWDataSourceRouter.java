package pers.binaryhunter.db.mybatis.datasource.impl;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.binaryhunter.db.mybatis.datasource.ConnectionHolder;
import pers.binaryhunter.db.mybatis.datasource.DataSourceRouter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.datasource.lookup.DataSourceLookup;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;

public abstract class AbstractRWDataSourceRouter implements DataSourceRouter, InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(AbstractRWDataSourceRouter.class);
	// 配置文件中配置的read-only datasoure
	// 可以为真实的datasource，也可以jndi的那种
	private List<Object> readDataSources;
	private Object writeDataSource;

	private DataSourceLookup dataSourceLookup = new JndiDataSourceLookup();

	private List<DataSource> resolvedReadDataSources;
	
	private DataSource resolvedWriteDataSource;
	// read-only data source的数量,做负载均衡的时候需要
	private int readDsSize;


	public List<DataSource> getResolvedReadDataSources() {
		return resolvedReadDataSources;
	}

	public int getReadDsSize() {
		return readDsSize;
	}

	public void setReadDataSources(List readDataSources) {
		this.readDataSources = readDataSources;
	}

	public void setWriteDataSource(Object writeDataSource) {
		this.writeDataSource = writeDataSource;
	}
	/**
	 * Set the DataSourceLookup implementation to use for resolving data source
	 * name Strings in the {@link #setTargetDataSources targetDataSources} map.
	 * <p>Default is a {@link JndiDataSourceLookup}, allowing the JNDI names
	 * of application server DataSources to be specified directly.
	 */
	public void setDataSourceLookup(DataSourceLookup dataSourceLookup) {
		this.dataSourceLookup = (dataSourceLookup != null ? dataSourceLookup : new JndiDataSourceLookup());
	}


	@Override
	public void afterPropertiesSet() {
			
		if (writeDataSource == null) {
			throw new IllegalArgumentException("Property 'writeDataSource' is required");
		}
		this.resolvedWriteDataSource = resolveSpecifiedDataSource(writeDataSource);

		if (this.readDataSources == null || this.readDataSources.size() == 0) {
			throw new IllegalArgumentException("Property 'resolvedReadDataSources' is required");
		}
		resolvedReadDataSources = new ArrayList<DataSource>(readDataSources.size());
		for (Object item : readDataSources) {
			resolvedReadDataSources.add(resolveSpecifiedDataSource(item));
		}
		readDsSize = readDataSources.size();
        logger.info("^_^ ReadDsSize " + readDsSize);
	}

	/**
	 * Resolve the specified data source object into a DataSource instance.
	 * <p>The default implementation handles DataSource instances and data source
	 * names (to be resolved via a {@link #setDataSourceLookup DataSourceLookup}).
	 * @param dataSource the data source value object as specified in the
	 * {@link #setTargetDataSources targetDataSources} map
	 * @return the resolved DataSource (never {@code null})
	 * @throws IllegalArgumentException in case of an unsupported value type
	 */
	protected DataSource resolveSpecifiedDataSource(Object dataSource) throws IllegalArgumentException {
		if (dataSource instanceof DataSource) {
			return (DataSource) dataSource;
		}
		else if (dataSource instanceof String) {
			return this.dataSourceLookup.getDataSource((String) dataSource);
		}
		else {
			throw new IllegalArgumentException(
					"Illegal data source value - only [javax.sql.DataSource] and String supported: " + dataSource);
		}
	}
	@Override
	public DataSource getTargetDataSource() {
		if (ConnectionHolder.WRITE.equals(ConnectionHolder.CURRENT_CONNECTION.get())) {
			return resolvedWriteDataSource;
		} else {
			return loadBalance();
		}
	}

	protected abstract DataSource loadBalance();
}
