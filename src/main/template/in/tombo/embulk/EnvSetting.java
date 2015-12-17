package in.tombo.embulk;

import lombok.Data;

@Data
public class EnvSetting {
  private String  gemsS3BucketName           = "@gemsS3BucketName@";
  private String  gemsS3Key                  = "@gemsS3Key@";
  private boolean useExternalConfig          = @useExternalConfig@;
  private String  externalConfigS3BucketName = "@externalConfigS3BucketName@";
  private String  externalConfigS3Key        = "@externalConfigS3Key@";
  private boolean overWriteExternalConfig    = @overWriteExternalConfig@;
}
