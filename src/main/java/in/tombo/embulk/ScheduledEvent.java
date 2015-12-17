package in.tombo.embulk;

import java.util.Map;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class ScheduledEvent {
  String              account;
  String              region;
  Map<String, String> detail;
  @JsonProperty(value = "detail-type")
  String              detailType;
  String              source;
  String              time;
  String              id;
  String[]            resources;
}
