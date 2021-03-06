import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.cache.CacheBuilderSpec;
import io.dropwizard.Configuration;
import io.dropwizard.db.DataSourceFactory;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

public class MyConfiguration extends Configuration {
    @NotEmpty
    private String authenticationCachePolicy;

    @NotEmpty
    private List<String> targetsOrigin;

    @NotNull
    private int tokenLifetime;

    @NotEmpty
    private String domain;

    @Valid
    @NotNull
    private DataSourceFactory database = new DataSourceFactory();

    @JsonProperty("database")
    public void setDataSourceFactory(DataSourceFactory factory) {
        this.database = factory;
    }

    @JsonProperty("database")
    public DataSourceFactory getDataSourceFactory() {
        return database;
    }

    @JsonProperty
    public CacheBuilderSpec getAuthenticationCachePolicy() {
        return CacheBuilderSpec.parse(authenticationCachePolicy);
    }

    @JsonProperty
    public int getTokenLifetime() {
        return tokenLifetime;
    }

    @JsonProperty
    public List getTargetsOrigin() {
        return targetsOrigin;
    }

    @JsonProperty
    public String getDomain() {
        return domain;
    }
}
