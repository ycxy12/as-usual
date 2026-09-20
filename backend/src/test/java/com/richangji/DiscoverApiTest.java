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
class DiscoverApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean WechatClient wechat;

  private String login(String name) throws Exception {
    when(wechat.exchange(name)).thenReturn("discover-" + name);
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
  void restaurantRecommendationFiltersRecentVisitsAndUpdatesVisit() throws Exception {
    String owner = login("restaurant-owner");
    String other = login("restaurant-other");
    String recent = LocalDate.now().minusDays(2).toString();
    mvc.perform(
            post("/api/v1/restaurants")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"最近火锅\",\"cuisine\":\"火锅\",\"averagePrice\":80,\"distanceKm\":2,\"lastVisitedDate\":\""
                        + recent
                        + "\",\"visitCount\":1}"))
        .andExpect(status().isCreated());
    String body =
        mvc.perform(
                post("/api/v1/restaurants")
                    .header("Authorization", owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"附近面馆\",\"cuisine\":\"面食\",\"averagePrice\":30,\"distanceKm\":1.5,\"visitCount\":0,\"tags\":[\"清淡\"]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(body).get("id").asText();
    mvc.perform(
            get("/api/v1/restaurants/recommend")
                .header("Authorization", owner)
                .param("maxBudget", "50")
                .param("maxDistance", "2")
                .param("visited", "NEVER")
                .param("excludeRecent7", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("附近面馆"));
    mvc.perform(get("/api/v1/restaurants").header("Authorization", owner).param("q", "清淡"))
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(post("/api/v1/restaurants/" + id + "/visit").header("Authorization", owner))
        .andExpect(jsonPath("$.visitCount").value(1))
        .andExpect(jsonPath("$.lastVisitedDate").value(LocalDate.now().toString()));
    mvc.perform(get("/api/v1/restaurants/" + id).header("Authorization", other))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/restaurants/recommend")
                .header("Authorization", owner)
                .param("visited", "NEVER")
                .param("maxBudget", "10"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("没有符合条件的候选，换个条件试试"));
  }

  @Test
  void placeRecommendationCrudAndVisitStayOwnerScoped() throws Exception {
    String owner = login("place-owner");
    String other = login("place-other");
    String payload =
        "{\"name\":\"滨江公园\",\"category\":\"公园\",\"address\":\"江边\",\"distanceKm\":4,\"averageCost\":0,\"recommendation\":5,\"visitStatus\":\"想去\",\"visitCount\":0}";
    String body =
        mvc.perform(
                post("/api/v1/places")
                    .header("Authorization", owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(body).get("id").asText();
    mvc.perform(
            get("/api/v1/places/recommend")
                .header("Authorization", owner)
                .param("category", "公园")
                .param("visited", "NEVER")
                .param("maxBudget", "20")
                .param("maxDistance", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
    mvc.perform(
            put("/api/v1/places/" + id)
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.replace("滨江公园", "滨江湿地公园")))
        .andExpect(jsonPath("$.name").value("滨江湿地公园"));
    mvc.perform(post("/api/v1/places/" + id + "/visit").header("Authorization", other))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/places/" + id + "/visit").header("Authorization", owner))
        .andExpect(jsonPath("$.visitStatus").value("去过"))
        .andExpect(jsonPath("$.visitCount").value(1));
    mvc.perform(get("/api/v1/places/stats").header("Authorization", owner))
        .andExpect(jsonPath("$.visited").value(1));
    mvc.perform(delete("/api/v1/places/" + id).header("Authorization", owner))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/places/" + id).header("Authorization", owner))
        .andExpect(status().isNotFound());
  }

  @Test
  void discoveryImageCannotCrossModulesAndIsDeletedWithOwner() throws Exception {
    String owner = login("discover-image-owner");
    MockMultipartFile image =
        new MockMultipartFile(
            "file",
            "food.jpg",
            "image/jpeg",
            new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1});
    String uploaded =
        mvc.perform(multipart("/api/v1/images").file(image).header("Authorization", owner))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String imageId = json.readTree(uploaded).get("id").asText();
    String body =
        mvc.perform(
                post("/api/v1/restaurants")
                    .header("Authorization", owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"图片餐厅\",\"cuisine\":\"中餐\",\"visitCount\":0,\"imageId\":\""
                            + imageId
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(body).get("id").asText();
    mvc.perform(
            post("/api/v1/places")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"图片地点\",\"category\":\"公园\",\"visitStatus\":\"想去\",\"visitCount\":0,\"imageId\":\""
                        + imageId
                        + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/v1/restaurants/" + id).header("Authorization", owner))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/images/" + imageId).header("Authorization", owner))
        .andExpect(status().isNotFound());
  }
}
