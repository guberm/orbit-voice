package dev.orbitvoice.core;
import java.io.*;
import java.util.function.Consumer;
import com.google.gson.*;
public class StreamReader {
    public static final int MAX_TEXT = 12000;
    public static class Update {
        public final String kind, text; public final JsonArray output;
        public Update(String kind,String text,JsonArray output) { this.kind=kind; this.text=text; this.output=output; }
    }
    public static void read(Reader reader, Consumer<Update> listener) throws IOException {
        BufferedReader lines = new BufferedReader(reader); StringBuilder data = new StringBuilder(), text = new StringBuilder(); String line;
        while ((line=lines.readLine())!=null) {
            if (line.length()>1000000) throw new IOException("Stream event too large");
            if (line.isEmpty()) {
                if (data.length()==0) continue;
                String raw=data.toString(); data.setLength(0);
                if (raw.equals("[DONE]")) break;
                try {
                    JsonObject event = JsonParser.parseString(raw).getAsJsonObject();
                    String type = JwtVerifier.value(event,"type");
                    if (type.equals("response.output_text.delta")) {
                        text.append(JwtVerifier.value(event,"delta"));
                        if (text.length()>MAX_TEXT) throw new IOException("response_too_long");
                        listener.accept(new Update("delta",text.toString(),null));
                    } else if (type.equals("response.completed")) {
                        JsonObject response = event.getAsJsonObject("response");
                        JsonArray output = response!=null && response.has("output") ? response.getAsJsonArray("output") : new JsonArray();
                        if (text.toString().trim().isEmpty()) throw new IOException("empty_response");
                        listener.accept(new Update("done",text.toString(),output)); return;
                    } else if (type.equals("response.failed") || type.equals("response.incomplete") || type.equals("error")) {
                        JsonObject container = event.has("response") ? event.getAsJsonObject("response") : event;
                        JsonObject error = container!=null && container.has("error") && container.get("error").isJsonObject() ? container.getAsJsonObject("error") : container;
                        String code=error==null ? "" : JwtVerifier.value(error,"code");
                        throw new IOException(code.isEmpty() ? type : code);
                    }
                } catch (JsonParseException | IllegalStateException | ClassCastException e) { throw new IOException("Invalid response stream",e); }
            } else if (line.startsWith("data:")) {
                if (data.length()>0) data.append('\n');
                data.append(line.substring(line.startsWith("data: ") ? 6 : 5));
                if (data.length()>1000000) throw new IOException("Stream event too large");
            }
        }
        throw new IOException("Stream interrupted before response.completed");
    }
}
