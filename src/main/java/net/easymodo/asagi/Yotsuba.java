package net.easymodo.asagi;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Yotsuba extends Board {
    
    public void newYotsubaPost() {
        
    }
    
    public void insertMediaPreview() {
        
    }
    
    public void insertMedia() {
        
    }
    
    public void insert() {
        
    }
    
    @Override
    public byte[] getMediaPreview(Post h) {
        // TODO Auto-generated method stub
        return null;
    }

    public void parsePost(String text, int parent) {
        String pattern = "<td \\s id=\"(\\d+)\"[^>]*> \\s*" +
            "<input[^>]*><span \\s class=\"replytitle\">(?>(.*?)</span>) \\s*"+
            "<span \\s class=\"commentpostername\">(?:<a \\s href=\"mailto:([^\"]*)\"[^>]*>)?(?:<span [^>]*>)?([^<]*?)(?:</span>)?(?:</a>)?</span>"+
            "(?: \\s* <span \\s class=\"postertrip\">(?:<span [^>]*>)?([a-zA-Z0-9\\.\\+/\\!]+)(?:</a>)?(?:</span>)?</span>)?"+
            "(?: \\s* <span \\s class=\"commentpostername\"><span [^>]*>\\#\\# \\s (.?)[^<]*</span>(?:</a>)?</span>)?"+
            "\\s* (?:<span \\s class=\"posttime\">)?([^<]*)(?:</span>)? \\s*"+
            "(?>.*?</span>) \\s*"+
            "(?: <br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; \\s*"+
                "<span \\s class=\"filesize\">File [^>]*"+
                "<a \\s href=\"([^\"]*/src/(\\d+\\.\\w+))\"[^>]*>[^<]*</a> \\s*"+
                "\\- \\s* \\((Spoiler \\s Image,)?([\\d\\sGMKB\\.]+)\\, \\s (\\d+)x(\\d+)(?:, \\s* <span \\s title=\"([^\"]*)\">[^<]*</span>)?\\)"+
                "</span> \\s*"+
                "(?: <br>\\s*<a[^>]*><img \\s+ src=\\S* \\s+ border=\\S* \\s+ align=\\S* \\s+ "+
                    "(?:width=(\\d+) \\s height=(\\d+))? [^>]*? md5=\"?([\\w\\d\\=\\+\\/]+)\"? [^>]*? ></a> \\s*"+
                    "| <a[^>]*><span \\s class=\"tn_reply\"[^>]*>Thumbnail \\s unavailable</span></a> )"+
                "| <br> \\s* <img [^>]* alt=\"File \\s deleted\\.\" [^>]* > \\s* )?"+
            "<blockquote>(?>(.*?)(<span \\s class=\"abbr\">(?:.*?))?</blockquote>)"+
            "</td></tr></table>";
        
        Pattern pat = Pattern.compile(pattern, Pattern.COMMENTS | Pattern.DOTALL);
        Matcher mat = pat.matcher(text);
        while(mat.find()) {
        
        String link = mat.group(8);
        String media_filename = mat.group(9);
        String spoiler = mat.group(10);
        String filesize = mat.group(11);
        String width = mat.group(12);
        String height = mat.group(13);
        String filename = mat.group(14);
        String twidth = mat.group(15);
        String theight = mat.group(16);
        String md5base64 = mat.group(17);
        String num = mat.group(1);
        String title = mat.group(2);
        String email = mat.group(3);
        String name = mat.group(4);
        String trip = mat.group(5);
        String capcode = mat.group(6);
        String date = mat.group(7);
        String sticky = "0";
        String comment = mat.group(18);
        String omitted = mat.group(19);
        System.out.println(
            String.format("link = %s\nmedia_filename = %s\nspoiler = %s\nfilesize = %s\nwidth = %s\n"+
                    "height = %s\nfilename = %s\ntwidth = %s\ntheight = %s\nmd5base64 = %s\n"+
                    "num = %s\ntitle = %s\nemail = %s\nname = %s\ntrip = %s\ncapcode = %s\ndate = %s\n"+
                    "sticky = %s\ncomment = %s\nomitted = %s\n",
                link, media_filename, spoiler, filesize, width, height, filename, twidth, theight,
                md5base64, num, title, email, name, trip, capcode, date, sticky, comment, omitted));
        }
    }
    
    public static void main(String[] args) throws IOException {
        Yotsuba yot = new Yotsuba();
        java.io.BufferedReader stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
        
        String text = new String();
        String newString = "";
        while((newString = stdin.readLine()) != null) {
            text += newString;
        }
        // System.out.println("your mom");
        yot.parsePost(text, 0);
    }
}
