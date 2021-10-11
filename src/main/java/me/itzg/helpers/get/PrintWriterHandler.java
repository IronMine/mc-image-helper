package me.itzg.helpers.get;

import java.io.IOException;
import java.io.PrintWriter;
import org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler;
import org.apache.hc.core5.http.HttpEntity;

class PrintWriterHandler extends AbstractHttpClientResponseHandler<String> {
  private final PrintWriter writer;

  public PrintWriterHandler(PrintWriter writer) {
    this.writer = writer;
  }

  @Override
  public String handleEntity(HttpEntity entity) throws IOException {

    EntityWriter.write(entity, writer);

    // no filename to return
    return "";
  }
}
