package com.richangji;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.richangji.auth.WechatClient;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssetApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean WechatClient wechat;

  private String login(String name) throws Exception {
    when(wechat.exchange(name)).thenReturn("asset-" + name);
    String body =
        mvc.perform(
                post("/api/v1/auth/wechat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"" + name + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + json.readTree(body).get("token").asText();
  }

  @Test
  void itemLifecycleStatsAndOwnership() throws Exception {
    String alice = login("item-alice");
    String bob = login("item-bob");
    String date = LocalDate.now().minusDays(9).toString();
    String payload =
        "{\"name\":\"相机\",\"category\":\"摄影器材\",\"purchaseDate\":\""
            + date
            + "\",\"purchasePrice\":1000,\"currentValue\":700,\"status\":\"在用\",\"tags\":[\"旅行\"]}";
    String body =
        mvc.perform(
                post("/api/v1/items")
                    .header("Authorization", alice)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.heldDays").value(10))
            .andExpect(jsonPath("$.depreciation").value(300))
            .andExpect(jsonPath("$.dailyCost").value(30))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(body).get("id").asText();
    mvc.perform(
            get("/api/v1/items")
                .header("Authorization", alice)
                .param("category", "摄影器材")
                .param("q", "旅行"))
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(get("/api/v1/items/stats").header("Authorization", alice))
        .andExpect(jsonPath("$.currentTotal").value(700));
    mvc.perform(get("/api/v1/items/" + id).header("Authorization", bob))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/v1/items/" + id)
                .header("Authorization", bob)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/v1/items/" + id)
                .header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.replace("相机", "旅行相机")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("旅行相机"));
    mvc.perform(get("/api/v1/items").header("Authorization", alice).param("q", "旅行相机"))
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(delete("/api/v1/items/" + id).header("Authorization", alice))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/items/" + id).header("Authorization", alice))
        .andExpect(status().isNotFound());
  }

  @Test
  void wardrobeWearLongUnwornAndImageCannotCrossModules() throws Exception {
    String owner = login("wardrobe-owner");
    String old = LocalDate.now().minusDays(100).toString();
    MockMultipartFile image =
        new MockMultipartFile(
            "file",
            "coat.png",
            "image/png",
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
    String uploaded =
        mvc.perform(multipart("/api/v1/images").file(image).header("Authorization", owner))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String imageId = json.readTree(uploaded).get("id").asText();
    String payload =
        "{\"name\":\"蓝外套\",\"category\":\"外套\",\"purchaseDate\":\""
            + old
            + "\",\"purchasePrice\":200,\"wearCount\":0,\"status\":\"在穿\",\"imageId\":\""
            + imageId
            + "\"}";
    String body =
        mvc.perform(
                post("/api/v1/wardrobe")
                    .header("Authorization", owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.longUnworn").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(body).get("id").asText();
    mvc.perform(
            post("/api/v1/items")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"相机\",\"category\":\"摄影器材\",\"status\":\"在用\",\"imageId\":\""
                        + imageId
                        + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/fragments")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"content\":\"占用测试\",\"occurredAt\":\"2026-09-18T12:00:00\",\"imageIds\":[\""
                        + imageId
                        + "\"]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/wardrobe/stats").header("Authorization", owner))
        .andExpect(jsonPath("$.longUnworn").value(1));
    mvc.perform(post("/api/v1/wardrobe/" + id + "/wear").header("Authorization", owner))
        .andExpect(jsonPath("$.wearCount").value(1))
        .andExpect(jsonPath("$.costPerWear").value(200))
        .andExpect(jsonPath("$.longUnworn").value(false));
    mvc.perform(post("/api/v1/wardrobe/" + id + "/wear").header("Authorization", owner))
        .andExpect(jsonPath("$.wearCount").value(2));
    mvc.perform(delete("/api/v1/wardrobe/" + id).header("Authorization", owner))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/images/" + imageId).header("Authorization", owner))
        .andExpect(status().isNotFound());
  }
}
