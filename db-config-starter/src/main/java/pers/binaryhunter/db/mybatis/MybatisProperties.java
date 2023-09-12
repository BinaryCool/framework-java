package pers.binaryhunter.db.mybatis;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration properties for Mybatis.
 *
 */
@ConfigurationProperties(prefix = "mybatis")
public class MybatisProperties {
    private static final Logger logger = LoggerFactory.getLogger(MybatisProperties.class);

    private static final String DEFAULT_RESOURCE_PATTERN = "**/*.class";

	/**
	 * Config file path.
	 */
	private String configLocation;

	/**
	 * Location of mybatis mapper files.
	 */
	private String[] mapperLocations;

	/**
	 * Package to scan repository objects.
	 */
	private String typeAliasesPackage;

	/**
	 * Package to scan handlers.
	 */
	private String typeHandlersPackage;

	/**
	 * Check the config file exists.
	 */
	private boolean checkConfigLocation = false;

	/**
	 * Execution mode for {@link org.mybatis.spring.SqlSessionTemplate}.
	 */
	private ExecutorType executorType;

	/**
	 * A Configuration object for customize default settings. If
	 * {@link #configLocation} is specified, this property is not used.
	 */
	@NestedConfigurationProperty
	private Configuration configuration;

	/**
	 * Is it a separate table
	 */
	private boolean separateTable = false;

	/**
	 * @since 1.1.0
	 * @return
	 */
	public String getConfigLocation() {
		return this.configLocation;
	}

	/**
	 * @since 1.1.0
	 * @return
	 */
	public void setConfigLocation(String configLocation) {
		this.configLocation = configLocation;
	}

	@Deprecated
	public String getConfig() {
		return this.configLocation;
	}

	@Deprecated
	public void setConfig(String config) {
		this.configLocation = config;
	}

	public String[] getMapperLocations() {
		return this.mapperLocations;
	}

	public void setMapperLocations(String[] mapperLocations) {
		this.mapperLocations = mapperLocations;
	}

	public String getTypeHandlersPackage() {
		return this.typeHandlersPackage;
	}

	public void setTypeHandlersPackage(String typeHandlersPackage) {
		this.typeHandlersPackage = typeHandlersPackage;
	}

	public String getTypeAliasesPackage() {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
        String typeAliasesPackageTmp = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                ClassUtils.convertClassNameToResourcePath(typeAliasesPackage) + "/" + DEFAULT_RESOURCE_PATTERN;

        //将加载多个绝对匹配的所有Resource
        //将首先通过ClassLoader.getResource("META-INF")加载非模式路径部分
        //然后进行遍历模式匹配
        try {
            List<String> result = new ArrayList<String>();
            Resource[] resources =  resolver.getResources(typeAliasesPackageTmp);
            if(resources != null && resources.length > 0){
                MetadataReader metadataReader = null;
                String name = null;
                for(Resource resource : resources){
                    if(resource.isReadable()){
                        metadataReader =  metadataReaderFactory.getMetadataReader(resource);
                        try {
                            name = Class.forName(metadataReader.getClassMetadata().getClassName()).getPackage().getName();
                            if(!result.contains(name)) {
                                result.add(name);
                            }
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            if(result.size() > 0) {
                typeAliasesPackageTmp = StringUtils.join(result, ",");
                return typeAliasesPackageTmp;
            }else{
                logger.warn("TypeAliasesPackage: " + this.typeAliasesPackage + ", can not find any package");
            }
        } catch (IOException e) {
            logger.error("", e);
        }

        return this.typeAliasesPackage;
	}

	public void setTypeAliasesPackage(String typeAliasesPackage) {
		this.typeAliasesPackage = typeAliasesPackage;
	}

	public boolean isCheckConfigLocation() {
		return this.checkConfigLocation;
	}

	public void setCheckConfigLocation(boolean checkConfigLocation) {
		this.checkConfigLocation = checkConfigLocation;
	}

	public ExecutorType getExecutorType() {
		return this.executorType;
	}

	public void setExecutorType(ExecutorType executorType) {
		this.executorType = executorType;
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

	public boolean isSeparateTable() {
		return separateTable;
	}

	public void setSeparateTable(boolean separateTable) {
		this.separateTable = separateTable;
	}

	public Resource[] resolveMapperLocations() {
		ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
		List<Resource> resources = new ArrayList<Resource>();
		if (this.mapperLocations != null) {
			for (String mapperLocation : this.mapperLocations) {
				try {
					Resource[] mappers = resourceResolver.getResources(mapperLocation);
					resources.addAll(Arrays.asList(mappers));
				} catch (IOException e) {
					// ignore
				}
			}
		}
		return resources.toArray(new Resource[resources.size()]);
	}
}
