package pers.binaryhunter.db.es.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @author kevin
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "binaryhunter.es")
public class EsProperties {
    private List<String> hosts;
}
