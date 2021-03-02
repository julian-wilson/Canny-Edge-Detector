import java.awt.image.BufferedImage;

public class Image {
    private double[][] r;
    private double[][] g;
    private double[][] b;
    private double[][] a;
    private double[][] l;
    private int width;
    private int height;

    public Image(BufferedImage i) {
        width = i.getWidth();
        height = i.getHeight();
        r = new double[width][height];
        g = new double[width][height];
        b = new double[width][height];
        a = new double[width][height];
        l = new double[width][height];
        for(int x = 0; x < width; x++) {
            for(int y = 0; y < height; y++) {
                int rgba = i.getRGB(x, y);
                int alpha = (rgba >> 24) & 0xFF;    //https://stackoverflow.com/questions/6001211/format-of-type-int-rgb-and-type-int-argb
                int red =   (rgba >> 16) & 0xFF;
                int green = (rgba >>  8) & 0xFF;
                int blue =  (rgba) & 0xFF;
                r[x][y] = (double)(red) / 255;
                g[x][y] = (double)(green) / 255;
                b[x][y] = (double)(blue) / 255;
                a[x][y] = (double)(alpha) / 255;
                l[x][y] = 0.299*r[x][y] + 0.587*g[x][y] + 0.114*b[x][y];    //converts rgb to lightness/grayscale
            }
        }
    }

    public double getRed(int x, int y) {
        return r[x][y];
    }
    public double[][] getRedArray() { return r.clone(); }
    public double getGreen(int x, int y) { return g[x][y]; }
    public double[][] getGreenArray() { return g.clone(); }
    public double getBlue(int x, int y) {
        return b[x][y];
    }
    public double[][] getBlueArray() { return b.clone(); }
    public double getLightness(int x, int y) {return l[x][y]; }
    public double[][] getLightnessArray() { return l.clone(); }
    public double getAlpha(int x, int y) { return a[x][y]; }
    public double[][] getAlphaArray() { return a.clone(); }
    public double getWidth() { return width; }
    public double getHeight() { return height; }

    public void setRed(int x, int y, double new_red) {
        if(new_red > 10) System.out.println("Something may be wrong");
        r[x][y] = new_red;
    }
    public void setGreen(int x, int y, double new_green) {
        if(new_green > 10) System.out.println("Something may be wrong");
        g[x][y] = new_green;
    }
    public void setBlue(int x, int y, double new_blue) {
        if(new_blue > 10) System.out.println("Something may be wrong");
        b[x][y] = new_blue;
    }
    public void setAlpha(int x, int y, double new_alpha) {
        a[x][y] = new_alpha;
    }
    public void setLightness(int x, int y, double new_lightness) {
        l[x][y] = new_lightness;
    }


}
