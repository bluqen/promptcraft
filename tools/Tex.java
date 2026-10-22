import java.awt.image.BufferedImage; import javax.imageio.ImageIO; import java.io.File; import java.util.Map;
public class Tex {
  static void w(String out, String[] rows, Map<Character,Integer> p, int scale) throws Exception {
    BufferedImage img = new BufferedImage(16*scale, 16*scale, BufferedImage.TYPE_INT_ARGB);
    for (int y=0;y<16*scale;y++) for (int x=0;x<16*scale;x++) img.setRGB(x,y,p.get(rows[y/scale].charAt(x/scale)));
    ImageIO.write(img, "png", new File(out));
  }
  public static void main(String[] a) throws Exception {
    Map<Character,Integer> p = Map.of('o',0xFF5A4E44,'c',0xFFCFC6B8,'l',0xFFE6DFD3,'d',0xFFA89F91,'s',0xFF121212,'O',0xFFD97757,'g',0xFF3A3A3A,'r',0xFFC0392B);
    String[] front = {
      "oooooooooooooooo","occccccccccccclo","ocssssssssssssco","ocssssssssssssco","ocsssssOsssssdco","ocssOssOssOsssco","ocsssOsOsOssssco","ocssssOOOsssssco",
      "ocsOOOOOOOOOssco","ocssssOOOsssssco","ocsssOsOsOssssco","ocssOssOssOsssco","ocssssssssssssco","occcccccccggrcco","oddddddddddddddo","oooooooooooooooo"};
    String[] side = {
      "oooooooooooooooo","olllllllllllllco","olccccccccccccdo","olccccccccccccdo","olccgggggggcccdo","olccccccccccccdo","olccgggggggcccdo","olccccccccccccdo",
      "olccgggggggcccdo","olccccccccccccdo","olccccccccccccdo","olccccccccccccdo","olccccccccccccdo","olccccccccccccdo","oddddddddddddddo","oooooooooooooooo"};
    String[] top = {
      "oooooooooooooooo","olllllllllllllco","olccccccccccccdo","olccccccccccccdo","olccccccccccccdo","olccccccccccccdo","olccccccccccccdo","olccccccccccccdo",
      "olccccccccccccdo","olccccccccccccdo","olccccccccccccdo","olccccccccccccdo","olccccccccccccdo","olccccccccccccdo","oddddddddddddddo","oooooooooooooooo"};
    String dir = a[0];
    w(dir+"/textures/block/computer_front.png", front, p, 1);
    w(dir+"/textures/block/computer_side.png", side, p, 1);
    w(dir+"/textures/block/computer_top.png", top, p, 1);
    w(dir+"/icon.png", front, p, 8);
  }
}
