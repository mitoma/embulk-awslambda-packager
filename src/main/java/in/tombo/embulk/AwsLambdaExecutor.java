package in.tombo.embulk;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jruby.embed.LocalContextScope;
import org.jruby.embed.ScriptingContainer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

public class AwsLambdaExecutor {

  private String       basePath   = String.format("/%s/%s/%s", "tmp", "embulk",
                                      UUID.randomUUID().toString());
  private String       gemJarPath = String.format("file:%s/gems.zip", basePath);
  private EnvSetting   envSetting = new EnvSetting();
  private LambdaLogger logger     = new LambdaLogger() {
                                    @Override
                                    public void log(String string) {
                                      System.out.println(string);
                                    }
                                  };

  /* for local testing */
  public static void main(String[] args) throws IOException {
    new AwsLambdaExecutor().execute();
  }

  public AwsLambdaExecutor() {
    changeDefaultCharset();
  }

  private void changeDefaultCharset() {
    try {
      System.setProperty("file.encoding", "UTF-8");
      Field field = Charset.class.getDeclaredField("defaultCharset");
      field.setAccessible(true);
      field.set(null, null);
    } catch (NoSuchFieldException | SecurityException
        | IllegalArgumentException | IllegalAccessException e) {
      throw new RuntimeException("('o') < broken java vm!!!");
    }
  }

  public String executeByScheduledEvent(ScheduledEvent args, Context context)
      throws IOException {
    logger = context.getLogger();
    logger.log("scheduled event start!");
    execute();
    logger.log("scheduled event finish!");
    return null;
  }

  public String executeByS3Event(S3Event s3event, Context context)
      throws IOException {
    logger = context.getLogger();
    logger.log("s3 event start!");
    execute();
    logger.log("s3 event finish!");
    return null;
  }

  public String execute() throws IOException {
    downloadGemsZip();

    String gemHome = extractGems();
    String configPath = String.format("/%s/config/config.yml", gemHome);

    if (envSetting.isUseExternalConfig()) {
      downloadConfig(configPath);
    }

    executeEmbulk(gemHome, configPath);

    if (envSetting.isOverWriteExternalConfig()
        && envSetting.isUseExternalConfig()) {
      overWriteExternalConfig(configPath);
    }

    return null;
  }

  private void overWriteExternalConfig(String configPath) {
    logger.log("overWriteExternalConfig start");
    AmazonS3Client client = new AmazonS3Client();
    String bucketName = envSetting.getExternalConfigS3BucketName();
    String key = envSetting.getExternalConfigS3Key();
    logger.log(String.format("bucketName:%s, key:%s, configPath:%s",
        bucketName, key, configPath));
    client.putObject(bucketName, key, new File(configPath));
    logger.log("overWriteExternalConfig finish");
  }

  private void executeEmbulk(String gemHome, String configPath) {
    logger.log("executeEmbulk start");
    ScriptingContainer jruby = new ScriptingContainer(
        LocalContextScope.SINGLETON);
    jruby
        .runScriptlet(String.format(
            "Dir['%s/gems/*/lib'].each {|path| $LOAD_PATH.unshift(path)}",
            gemHome));

    jruby.runScriptlet(""//
        + "require 'embulk';"//
        + "require 'embulk/java/bootstrap';"//
        + "Embulk.setup");
    logger.log("executeEmbulk run");
    jruby.runScriptlet(String.format(
        "Embulk::Runner.run('%s', {next_config_output_path: '%s'})",
        configPath, configPath));
    logger.log("executeEmbulk finish");
  }

  private void downloadConfig(String configPath) throws IOException {
    logger.log("loadConfig start");
    String bucket = envSetting.getExternalConfigS3BucketName();
    String key = envSetting.getExternalConfigS3Key();
    String writePath = configPath;
    downloadFromS3(bucket, key, writePath);
    logger.log("loadConfig finish");
  }

  private void downloadGemsZip() throws IOException {
    logger.log("dounloadGemZip start");
    String bucket = envSetting.getGemsS3BucketName();
    String key = envSetting.getGemsS3Key();
    String writePath = basePath + "/gems.zip";
    downloadFromS3(bucket, key, writePath);
    logger.log("dounloadGemZip finish");
  }

  private void downloadFromS3(String bucket, String key, String writePath)
      throws IOException, FileNotFoundException {
    logger.log(String.format("dounloadFromS3 bucket:%s, key:%s, writePath:%s",
        bucket, key, writePath));
    AmazonS3Client client = new AmazonS3Client();
    S3Object object = client.getObject(bucket, key);
    File file = new File(writePath);
    file.getParentFile().mkdirs();
    try (S3ObjectInputStream strm = object.getObjectContent();
        FileOutputStream fileOutputStream = new FileOutputStream(file)) {
      byte[] buffer = new byte[4096];
      int readSize = 0;
      while ((readSize = strm.read(buffer)) != -1) {
        fileOutputStream.write(buffer, 0, readSize);
      }
    }
  }

  private String extractGems() throws IOException {
    logger.log("extractGems start");
    URL jar = new URL(gemJarPath);
    try (ZipInputStream zip = new ZipInputStream(jar.openStream())) {
      while (true) {
        ZipEntry entry = zip.getNextEntry();
        if (entry == null) {
          break;
        }
        if (entry.isDirectory()) {
          continue;
        }
        extractFile(basePath, zip, entry);
      }
    }
    logger.log("extractGems finish");
    return basePath;
  }

  private void extractFile(String basePath, ZipInputStream zip, ZipEntry entry)
      throws IOException, FileNotFoundException {
    Path targetPath = Paths.get(basePath, entry.getName());
    BufferedInputStream in = new BufferedInputStream(zip);
    if (!targetPath.toFile().getParentFile().exists()) {
      targetPath.toFile().getParentFile().mkdirs();
    }
    try (BufferedOutputStream out = new BufferedOutputStream(
        new FileOutputStream(targetPath.toFile()))) {
      byte[] buffer = new byte[4096];
      int readSize = 0;
      while ((readSize = in.read(buffer)) != -1) {
        out.write(buffer, 0, readSize);
      }
    }
  }
}