package com.parallax.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Assistant configuration (SPEC §12): the model id, application-service URL and the assistant's creds. */
@ConfigurationProperties(prefix = "parallax")
public class AssistantProperties {

    private final Assistant assistant = new Assistant();
    private final Application application = new Application();
    private final Credentials credentials = new Credentials();

    public Assistant getAssistant() {
        return assistant;
    }

    public Application getApplication() {
        return application;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public static class Assistant {
        private String model;
        private int maxTokens = 1024;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public static class Application {
        private String url;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class Credentials {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
