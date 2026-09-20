package com.richangji;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.richangji.auth.WechatClient;
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
class RecordApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean WechatClient wechat;

  private String login(String code, String openid) throws Exception {
    when(wechat.exchange(code)).thenReturn(openid);
    String body =
        mvc.perform(
                post("/api/v1/auth/wechat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"" + code + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + json.readTree(body).get("token").asText();
  }

  @Test
  void recordsAreScopedToWechatIdentity() throws Exception {
    String alice = login("alice-code", "alice-openid");
    String bob = login("bob-code", "bob-openid");
    String body =
        mvc.perform(
                post("/api/v1/fragments")
                    .header("Authorization", alice)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"content\":\"今天去了公园\",\"occurredAt\":\"2026-09-18T12:00:00\",\"tags\":[\"生活\"],\"imageIds\":[]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = json.readTree(body).get("id").asText();
    mvc.perform(get("/api/v1/fragments/" + id).header("Authorization", alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tags[0]").value("生活"));
    mvc.perform(get("/api/v1/fragments/" + id).header("Authorization", bob))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/fragments/" + id).header("Authorization", bob))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/fragments").header("Authorization", bob))
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/v1/fragments")).andExpect(status().isUnauthorized());
  }

  @Test
  void vehicleHousingAndSocialDataSurviveRoundTrips() throws Exception {
    String owner = login("another-code", "another-openid");
    mvc.perform(
            post("/api/v1/vehicles")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"occurredAt\":\"2026-09-18T12:00:00\",\"category\":\"加油\",\"amount\":123.45,\"odometer\":1000}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount").value(123.45));
    mvc.perform(get("/api/v1/vehicles/stats").header("Authorization", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expenses.total").value(123.45));
    mvc.perform(
            post("/api/v1/housing")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"occurredAt\":\"2026-09-18T12:00:00\",\"category\":\"电费\",\"amount\":88,\"billingMonth\":\"2026-09\"}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/api/v1/housing/stats").header("Authorization", owner))
        .andExpect(jsonPath("$.expenses.total").value(88));
    String contactJson =
        mvc.perform(
                post("/api/v1/contacts")
                    .header("Authorization", owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"小林\",\"relation\":\"朋友\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String contactId = json.readTree(contactJson).get("id").asText();
    mvc.perform(
            post("/api/v1/contacts/" + contactId + "/gifts")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"occurredAt\":\"2026-09-18T12:00:00\",\"event\":\"生日\",\"direction\":\"送出\",\"kind\":\"现金\",\"amount\":200}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/api/v1/contacts/" + contactId).header("Authorization", owner))
        .andExpect(jsonPath("$.sent").value(200));
    mvc.perform(get("/api/v1/contacts/" + contactId + "/gifts").header("Authorization", owner))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void imageDownloadRequiresOwnerAndFragmentDeleteRemovesImage() throws Exception {
    String alice = login("image-alice-code", "image-alice-openid");
    String bob = login("image-bob-code", "image-bob-openid");
    MockMultipartFile image =
        new MockMultipartFile(
            "file",
            "photo.jpg",
            "image/jpeg",
            new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3});
    String uploaded =
        mvc.perform(multipart("/api/v1/images").file(image).header("Authorization", alice))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String imageId = json.readTree(uploaded).get("id").asText();
    mvc.perform(get("/api/v1/images/" + imageId).header("Authorization", alice))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/images/" + imageId).header("Authorization", bob))
        .andExpect(status().isNotFound());
    String temporaryUpload =
        mvc.perform(
                multipart("/api/v1/images")
                    .file(
                        new MockMultipartFile(
                            "file",
                            "temporary.jpg",
                            "image/jpeg",
                            new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 4, 5, 6}))
                    .header("Authorization", alice))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String temporaryImageId = json.readTree(temporaryUpload).get("id").asText();
    mvc.perform(delete("/api/v1/images/" + temporaryImageId).header("Authorization", bob))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/images/" + temporaryImageId).header("Authorization", alice))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/images/" + temporaryImageId).header("Authorization", alice))
        .andExpect(status().isNotFound());
    String fragment =
        mvc.perform(
                post("/api/v1/fragments")
                    .header("Authorization", alice)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"content\":\"一张照片\",\"occurredAt\":\"2026-09-18T12:00:00\",\"imageIds\":[\""
                            + imageId
                            + "\"]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String fragmentId = json.readTree(fragment).get("id").asText();
    mvc.perform(delete("/api/v1/images/" + imageId).header("Authorization", alice))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/fragments/" + fragmentId).header("Authorization", alice))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/images/" + imageId).header("Authorization", alice))
        .andExpect(status().isNotFound());
  }

  @Test
  void fragmentSearchTagDateAndPaginationAgree() throws Exception {
    String owner = login("filter-code", "filter-openid");
    mvc.perform(
            post("/api/v1/fragments")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"content\":\"公园散步\",\"occurredAt\":\"2026-09-18T08:00:00\",\"tags\":[\"周末\"]}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/fragments")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"content\":\"工作灵感\",\"occurredAt\":\"2026-09-17T09:00:00\",\"tags\":[\"工作\"]}"))
        .andExpect(status().isCreated());
    mvc.perform(
            get("/api/v1/fragments")
                .header("Authorization", owner)
                .param("q", "公园")
                .param("tag", "周末")
                .param("date", "2026-09-18"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].content").value("公园散步"));
    mvc.perform(
            get("/api/v1/fragments")
                .header("Authorization", owner)
                .param("page", "1")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items.length()").value(1));
  }
}
