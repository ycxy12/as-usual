package com.richangji;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
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
class ProfileApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean WechatClient wechat;

  private String login(String name) throws Exception {
    when(wechat.exchange(name)).thenReturn("profile-" + name);
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
  void categoriesRenameBusinessDataAndTagsMergeAcrossModules() throws Exception {
    String owner = login("organize-owner");
    String list =
        mvc.perform(
                get("/api/v1/profile/categories")
                    .header("Authorization", owner)
                    .param("module", "item"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(7))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode categories = json.readTree(list);
    String digitalId = "";
    for (JsonNode category : categories)
      if (category.get("name").asText().equals("数码产品")) digitalId = category.get("id").asText();
    mvc.perform(
            post("/api/v1/items")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"手机\",\"category\":\"数码产品\",\"status\":\"在用\",\"tags\":[\"旅行\",\"常用\"]}"))
        .andExpect(status().isCreated());
    mvc.perform(
            put("/api/v1/profile/categories/" + digitalId)
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"电子产品\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("电子产品"));
    mvc.perform(get("/api/v1/items").header("Authorization", owner).param("category", "电子产品"))
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(delete("/api/v1/profile/categories/" + digitalId).header("Authorization", owner))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/v1/fragments")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"content\":\"周末散步\",\"occurredAt\":\""
                        + LocalDate.now()
                        + "T10:00:00\",\"tags\":[\"出游\"]}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/profile/tags/rename")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldName\":\"旅行\",\"newName\":\"出游\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.usageCount").value(2));
    mvc.perform(get("/api/v1/profile/tags").header("Authorization", owner).param("q", "出游"))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].usageCount").value(2));
    mvc.perform(delete("/api/v1/profile/tags").header("Authorization", owner).param("name", "出游"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/profile/tags").header("Authorization", owner).param("q", "出游"))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void overviewAndClearAffectOnlyCurrentOwnerAndRemoveImages() throws Exception {
    String alice = login("clear-alice");
    String bob = login("clear-bob");
    String now = LocalDate.now() + "T12:00:00";
    mvc.perform(
            post("/api/v1/vehicles")
                .header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"occurredAt\":\"" + now + "\",\"category\":\"停车\",\"amount\":20}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/fragments")
                .header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Alice 的记录\",\"occurredAt\":\"" + now + "\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/fragments")
                .header("Authorization", bob)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Bob 的记录\",\"occurredAt\":\"" + now + "\"}"))
        .andExpect(status().isCreated());
    MockMultipartFile image =
        new MockMultipartFile(
            "file",
            "private.png",
            "image/png",
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
    String uploaded =
        mvc.perform(multipart("/api/v1/images").file(image).header("Authorization", alice))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String imageId = json.readTree(uploaded).get("id").asText();
    mvc.perform(get("/api/v1/profile/overview").header("Authorization", alice))
        .andExpect(jsonPath("$.monthRecords").value(2))
        .andExpect(jsonPath("$.monthVehicleCost").value(20));
    mvc.perform(delete("/api/v1/profile/data").header("Authorization", alice))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/profile/data-stats").header("Authorization", alice))
        .andExpect(jsonPath("$.fragments").value(0))
        .andExpect(jsonPath("$.vehicles").value(0))
        .andExpect(jsonPath("$.images").value(0));
    mvc.perform(get("/api/v1/images/" + imageId).header("Authorization", alice))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/fragments").header("Authorization", bob))
        .andExpect(jsonPath("$.total").value(1));
  }
}
