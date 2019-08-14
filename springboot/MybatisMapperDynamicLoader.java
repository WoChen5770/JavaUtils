import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Created with IntelliJ IDEA.
 * http://ichenqiang.com
 *
 * @version 1.0
 * @Date: 2018/12/27 13:48
 * @Description:mapper.xml热加载
 */
@Component
public class MybatisMapperDynamicLoader implements InitializingBean, ApplicationContextAware{

    private final HashMap mappers = new HashMap();
    private volatile ConfigurableApplicationContext context = null;
    private volatile Scanner scanner = null;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = (ConfigurableApplicationContext) applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        try {
            scanner = new Scanner();
            new Timer(true).schedule(new TimerTask() {
                public void run() {
                    try {
                        if (scanner.isChanged()) {
                            scanner.reloadXML();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 10 * 1000, 5 * 1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class Scanner {
        private static final String XML_RESOURCE_PATTERN = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                + "**/*Mapper.xml";
        private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

        public Scanner() throws IOException {
            Resource[] resources = findResource();
            if (resources != null) {
                for (Resource resource : resources) {
                    String key = resource.getURI().toString();
                    String value = getMd(resource);
                    mappers.put(key, value);
                }
            }
        }

        public void reloadXML() throws Exception {
            SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
            Configuration configuration = factory.getConfiguration();
            removeConfig(configuration);
            for (Resource resource : findResource()) {
                try {
                    XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(resource.getInputStream(), configuration,
                            resource.toString(), configuration.getSqlFragments());
                    xmlMapperBuilder.parse();
                } finally {
                    ErrorContext.instance().reset();
                }
            }
        }

        private void removeConfig(Configuration configuration) throws Exception {
            clearMap(configuration, "mappedStatements");
            clearMap(configuration, "caches");
            clearMap(configuration, "resultMaps");
            clearMap(configuration, "parameterMaps");
            clearMap(configuration, "keyGenerators");
            clearMap(configuration, "sqlFragments");
            clearSet(configuration, "loadedResources");
        }

        @SuppressWarnings("rawtypes")
        private void clearMap(Configuration configuration, String fieldName) throws Exception {
            Field field = Configuration.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            ((Map) field.get(configuration)).clear();
        }

        @SuppressWarnings("rawtypes")
        private void clearSet(Configuration configuration, String fieldName) throws Exception {
            Field field = Configuration.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            ((Set) field.get(configuration)).clear();
        }

        public boolean isChanged() throws IOException {
            boolean isChanged = false;
            for (Resource resource : findResource()) {
                String key = resource.getURI().toString();
                String value = getMd(resource);
                if (!value.equals(mappers.get(key))) {
                    System.out.println(key+" reload success");
                    isChanged = true;
                    mappers.put(key, value);
                }
            }
            return isChanged;
        }

        private Resource[] findResource() throws IOException {
            return resourcePatternResolver.getResources(XML_RESOURCE_PATTERN);
        }

        private String getMd(Resource resource) throws IOException {
            return new StringBuilder().append(resource.contentLength()).append("-").append(resource.lastModified())
                    .toString();
        }
    }
}
