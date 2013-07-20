package net.easymodo.asagi;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.*;

import net.easymodo.asagi.model.MediaPost;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.apache.http.annotation.ThreadSafe;

import net.easymodo.asagi.exception.ContentGetException;
import net.easymodo.asagi.exception.ContentParseException;
import net.easymodo.asagi.model.yotsuba.*;

@ThreadSafe
public abstract class Yotsuba extends WWW {
    protected static final Gson GSON = new GsonBuilder().
            registerTypeAdapter(boolean.class, new BooleanTypeConverter()).
            setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    protected static class BooleanTypeConverter implements JsonSerializer<Boolean>, JsonDeserializer<Boolean> {
        @Override
        public JsonElement serialize(Boolean src, Type srcType, JsonSerializationContext context) {
            return new JsonPrimitive(src ? 1 : 0);
        }

        @Override
        public Boolean deserialize(JsonElement json, Type type, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                return (json.getAsInt() != 0);
            } catch (ClassCastException e) {
                // fourchan api can't make up its mind about this
                return json.getAsBoolean();
            }
        }
    }
    protected static final Pattern exifPattern;
    protected static final Pattern exifDataPattern;

    static {
        String exifPatString = "<table \\s class=\"exif\"[^>]*>(.*)</table>";
        String exifDataPatString = "<tr><td>(.*?)</td><td>(.*?)</td></tr>";

        exifPattern = Pattern.compile(exifPatString, Pattern.COMMENTS | Pattern.DOTALL);
        exifDataPattern = Pattern.compile(exifDataPatString, Pattern.COMMENTS | Pattern.DOTALL);
    }

    protected Map<String,String> boardLinks;

    @Override
    public InputStream getMediaPreview(MediaPost h) throws ContentGetException {
        if (h.getPreview() == null)
            return null;

        return this.wget(this.boardLinks.get("previewLink") + "/thumb/" + h.getPreview());
    }

    @Override
    public InputStream getMedia(MediaPost h) throws ContentGetException {
        if (h.getMedia() == null)
            return null;

        return this.wget(this.boardLinks.get("imgLink") + "/src/" + h.getMedia());
    }

    public int parseDate(int dateUtc) {
        DateTime dtDate = new DateTime(dateUtc * 1000L);
        DateTime dtEst = dtDate.withZone(DateTimeZone.forID("America/New_York"));
        return (int) (dtEst.withZoneRetainFields(DateTimeZone.UTC).getMillis() / 1000);
    }

    public String cleanSimple(String text) {
        return super.doClean(text);
    }

    public String cleanLink(String text) {
        return super.doCleanLink(super.doClean(text));
    }

    public String doClean(String text) {
        if (text == null) return null;

        // SOPA spoilers
        //text = text.replaceAll("<span class=\"spoiler\"[^>]*>(.*?)</spoiler>(</span>)?", "$1");

        // Admin-Mod-Dev quotelinks
        text = text.replaceAll("<span class=\"capcodeReplies\"><span style=\"font-size: smaller;\"><span style=\"font-weight: bold;\">(?:Administrator|Moderator|Developer) Repl(?:y|ies):</span>.*?</span><br></span>", "");
        // Non-public tags
        text = text.replaceAll("\\[(banned|moot)]", "[$1:lit]");
        // Comment too long, also EXIF tag toggle
        text = text.replaceAll("<span class=\"abbr\">.*?</span>", "");
        // EXIF data
        text = text.replaceAll("<table class=\"exif\"[^>]*>.*?</table>", "");
        // Banned/Warned text
        text = text.replaceAll("<(?:b|strong) style=\"color:\\s*red;\">(.*?)</(?:b|strong)>", "[banned]$1[/banned]");
        // moot text
        text = text.replaceAll("<div style=\"padding: 5px;margin-left: \\.5em;border-color: #faa;border: 2px dashed rgba\\(255,0,0,\\.1\\);border-radius: 2px\">(.*?)</div>", "[moot]$1[/moot]");
        // bold text
        text = text.replaceAll("<(?:b|strong)>(.*?)</(?:b|strong)>", "[b]$1[/b]");
        // > implying I'm quoting someone
        text = text.replaceAll("<font class=\"unkfunc\">(.*?)</font>", "$1");
        text = text.replaceAll("<span class=\"quote\">(.*?)</span>", "$1");
        // Dead Quotes
        text = text.replaceAll("<span class=\"deadlink\">(.*?)</span>", "$1");
        text = text.replaceAll("<span class=\"quote deadlink\">(.*?)</span>", "$1");
        // Links
        text = text.replaceAll("<a[^>]*>(.*?)</a>", "$1");
        // Old Spoilers (start)
        text = text.replaceAll("<span class=\"spoiler\"[^>]*>", "[spoiler]");
        // Old Spoilers (end)
        text = text.replaceAll("</span>", "[/spoiler]");
        // Spoilers (start)
        text = text.replaceAll("<s>", "[spoiler]");
        // Spoilers (end)
        text = text.replaceAll("</s>", "[/spoiler]");
        // Newlines
        text = text.replaceAll("<br\\s*/?>", "\n");
        // WBR
        text = text.replaceAll("<wbr>", "");

        return this.cleanSimple(text);
    }

    public String parseExif(String text) {
        if (text == null) return null;

        Matcher exif = exifPattern.matcher(text);

        if (exif.find()) {
            String data = exif.group(1);
            // remove empty rows
            data = data.replaceAll("<tr><td colspan=\"2\"></td></tr><tr>", "");

            Map<String, String> exifJson = new HashMap<String, String>();
            Matcher exifData = exifDataPattern.matcher(data);

            while (exifData.find()) {
                String key = exifData.group(1);
                String val = exifData.group(2);
                exifJson.put(key, val);
            }

            if (exifJson.size() > 0)
                return GSON.toJson(exifJson);
        }

        return null;
    }
}
