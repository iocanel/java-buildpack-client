package dev.snowdrop.buildpack.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Image;

import java.util.*;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

/**
 * Higher level docker image api
 */
public class ImageUtils {
  private static final Logger log = Logger.getLogger(ImageUtils.class.getName());

  public static class ImageInfo {
    public String id;
    public Map<String, String> labels;
    public String[] env;
  }

  /**
   * Util method to pull images if they don't exist to the local docker yet.
   */
  public static void pullImages(DockerClient dc, int timeoutSeconds, String... imageNames) throws InterruptedException {
    Set<String> imageNameSet = new HashSet<>(Arrays.asList(imageNames));

    // list the current known images
    List<Image> li = dc.listImagesCmd().exec();
    for (Image i : li) {
      if (i.getRepoTags() == null) {
        continue;
      }
      for (String it : i.getRepoTags()) {
        if (imageNameSet.contains(it)) {
          imageNameSet.remove(it);
        }
      }
    }

    if (imageNameSet.isEmpty()) {
      // fast exit if all images are already known to the local docker.
      log.fine("Nothing to pull, all of " + Arrays.asList(imageNames) + " are known");
      return;
    }

    // pull the images not known
    List<PullImageResultCallback> pircs = new ArrayList<>();
    for (String stillNeeded : imageNameSet) {
      log.fine("pulling '" + stillNeeded + "'");
      PullImageResultCallback pirc = new PullImageResultCallback();
      dc.pullImageCmd(stillNeeded).exec(pirc);
      pircs.add(pirc);
    }

    // wait for pulls to complete.
    for (PullImageResultCallback pirc : pircs) {
      pirc.awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);
    }

    // TODO: progress tracking..
  }

  /**
   * Util method to retrieve info for a given docker image.
   */
  public static ImageInfo inspectImage(DockerClient dc, String imageName) {
    InspectImageResponse iir = dc.inspectImageCmd(imageName).exec();
    // today we just keep the id/labels/env, can expand if needed.
    ImageInfo ii = new ImageInfo();
    ii.id = iir.getId();
    ii.labels = iir.getConfig().getLabels();
    ii.env = iir.getConfig().getEnv();
    return ii;
  }
}
