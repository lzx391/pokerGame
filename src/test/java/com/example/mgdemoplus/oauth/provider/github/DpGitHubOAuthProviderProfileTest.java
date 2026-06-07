package com.example.mgdemoplus.oauth.provider.github;

import com.example.mgdemoplus.oauth.dto.OAuthUserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DpGitHubOAuthProviderProfileTest {

    @Test
    void githubUserInfo_deserializesLoginFromJson() throws Exception {
        String json = """
                {"login":"octocat","id":1,"avatar_url":"https://avatars.githubusercontent.com/u/1","name":"The Octocat"}
                """;

        GitHubUserInfo info = new ObjectMapper().readValue(json, GitHubUserInfo.class);

        assertThat(info.getLogin()).isEqualTo("octocat");
        assertThat(info.getId()).isEqualTo(1L);
        assertThat(info.getAvatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/1");
        assertThat(info.getName()).isEqualTo("The Octocat");
    }

    @Test
    void oauthUserProfile_usesLoginAsOpenId() {
        OAuthUserProfile profile = new OAuthUserProfile("octocat", "https://example.com/a.png", "The Octocat");

        assertThat(profile.getOpenId()).isEqualTo("octocat");
    }
}
