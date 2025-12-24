package nan.produced.prism.auth.security.config;


import com.aliyun.auth.credentials.provider.EnvironmentVariableCredentialProvider;
import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import darabonba.core.client.ClientOverrideConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AliyunConfig {

    EnvironmentVariableCredentialProvider provider = new EnvironmentVariableCredentialProvider();

    @Bean
    public AsyncClient aliyunClient() {
        return AsyncClient.builder()
                .region("cn-shenzhen")
                .credentialsProvider(provider)
                .overrideConfiguration(
                        ClientOverrideConfiguration.create()
                                .setEndpointOverride("dypnsapi.aliyuncs.com")
                )
                .build();
    }


}
