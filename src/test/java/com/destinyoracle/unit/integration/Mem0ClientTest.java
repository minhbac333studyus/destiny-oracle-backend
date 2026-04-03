package com.destinyoracle.unit.integration;

import com.destinyoracle.integration.Mem0Client;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@WireMockTest
class Mem0ClientTest {

    private Mem0Client mem0;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        // Use RestTemplate-based RestClient to avoid HTTP/2 issues with WireMock
        RestClient restClient = RestClient.builder(new RestTemplate())
            .baseUrl(wmInfo.getHttpBaseUrl())
            .build();
        mem0 = new Mem0Client(restClient);
    }

    @Test
    void searchMemories_returnsFormattedFacts() {
        // Mem0 v1 API returns a raw JSON array (not wrapped in {"results": [...]})
        stubFor(post("/v1/memories/search/")
            .willReturn(okJson("""
                [
                    {"memory": "User is vegetarian", "score": 0.92},
                    {"memory": "User has bad left knee", "score": 0.87}
                ]
                """)));

        String result = mem0.searchMemories("00000000-0000-0000-0000-000000000001", "leg workout");

        assertThat(result).contains("KNOWN FACTS");
        assertThat(result).contains("User is vegetarian");
        assertThat(result).contains("User has bad left knee");
    }

    @Test
    void searchMemories_emptyResults_returnsEmptyString() {
        stubFor(post("/v1/memories/search/")
            .willReturn(okJson("[]")));

        String result = mem0.searchMemories("00000000-0000-0000-0000-000000000001", "random query");

        assertThat(result).isEmpty();
    }

    @Test
    void searchMemories_serverDown_returnsEmptyGracefully() {
        stubFor(post("/v1/memories/search/")
            .willReturn(serverError()));

        String result = mem0.searchMemories("00000000-0000-0000-0000-000000000001", "query");

        assertThat(result).isEmpty(); // graceful degradation, no exception
    }

    @Test
    void addMemory_sendsCorrectPayload() {
        stubFor(post("/v1/memories/")
            .willReturn(okJson("""
                {"results": [{"id": "mem-1", "memory": "User is vegetarian"}]}
                """)));

        // Should not throw
        mem0.addMemory("00000000-0000-0000-0000-000000000001", "I am vegetarian", "Got it, noted.");

        verify(postRequestedFor(urlEqualTo("/v1/memories/"))
            .withRequestBody(matchingJsonPath("$.user_id", equalTo("00000000-0000-0000-0000-000000000001")))
            .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("user")))
            .withRequestBody(matchingJsonPath("$.messages[0].content",
                containing("vegetarian"))));
    }
}
