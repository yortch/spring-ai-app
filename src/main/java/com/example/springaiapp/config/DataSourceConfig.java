package com.example.springaiapp.config;

import com.azure.identity.AzureCliCredential;
import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.identity.ChainedTokenCredential;
import com.azure.identity.ChainedTokenCredentialBuilder;
import org.springframework.beans.factory.annotation.Value;
import com.azure.core.credential.TokenRequestContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    public ChainedTokenCredential createChainedCredential() {
        AzureCliCredential azureCliCredential = new AzureCliCredentialBuilder().build();
        ChainedTokenCredential credentialChain = new ChainedTokenCredentialBuilder()
            .addLast(azureCliCredential)
            .build();
        return credentialChain;
    }

    @Bean
    public DataSource dataSource() {
        ChainedTokenCredential credential = createChainedCredential();
        String accessToken = credential.getToken(
            new TokenRequestContext()
                .addScopes("https://ossrdbms-aad.database.windows.net"))
                .block().getToken();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(dbUrl);
        dataSource.setUsername(dbUsername);
        dataSource.setPassword(accessToken);
        return dataSource;
    }
}