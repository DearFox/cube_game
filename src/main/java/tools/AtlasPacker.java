package tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public final class AtlasPacker {

    public static void main(String[] args) throws Exception {
        // Adjust paths as you like
        Path inputDir = Paths.get("src/main/resources/tiles");          // folder of tile pngs
        Path outPng   = Paths.get("src/main/resources/atlas.png");
        Path outMap   = Paths.get("src/main/resources/atlas.txt");

        int tileSize = 16;
        int pad = 8;            // 2–4 recommended
        int columns = 16;       // how many tiles per row (pick a nice number)

        pack(inputDir, outPng, outMap, tileSize, pad, columns);
        System.out.println("Wrote " + outPng + " and " + outMap);
    }

    public static void pack(Path inputDir, Path outPng, Path outMap,
                            int tileSize, int pad, int columns) throws Exception {

        if (!Files.isDirectory(inputDir)) {
            throw new IllegalArgumentException("Not a directory: " + inputDir.toAbsolutePath());
        }

        List<Path> files = Files.list(inputDir)
                .filter(p -> p.toString().toLowerCase().endsWith(".png"))
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .collect(Collectors.toList());

        if (files.isEmpty()) {
            throw new IllegalStateException("No .png tiles found in " + inputDir.toAbsolutePath());
        }

        int cell = tileSize + pad * 2;
        int rows = (files.size() + columns - 1) / columns;

        int atlasW = columns * cell;
        int atlasH = rows * cell;

        BufferedImage atlas = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);

        // Write mapping as: name tileX tileY
        // where tileX,tileY are the grid indices (NOT pixels)
        StringBuilder map = new StringBuilder();
        map.append("# name tileX tileY\n");
        map.append("# tileSize=").append(tileSize).append(" pad=").append(pad)
                .append(" cell=").append(cell).append("\n");

        for (int i = 0; i < files.size(); i++) {
            Path p = files.get(i);
            BufferedImage src = ImageIO.read(p.toFile());
            if (src == null) throw new IOException("Failed to read " + p);

            // Ensure ARGB (some PNGs load as other types)
            if (src.getType() != BufferedImage.TYPE_INT_ARGB) {
                BufferedImage tmp = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
                tmp.getGraphics().drawImage(src, 0, 0, null);
                src = tmp;
            }

            // IMPORTANT: alpha-bleed BEFORE copying into atlas and extruding padding
            // iterations should be at least pad
            src = alphaBleed(src, pad, 0);
            if (src == null) throw new IOException("Failed to read " + p);

            if (src.getWidth() != tileSize || src.getHeight() != tileSize) {
                throw new IllegalArgumentException(
                        "Tile " + p.getFileName() + " must be " + tileSize + "x" + tileSize +
                                " but is " + src.getWidth() + "x" + src.getHeight());
            }

            int tx = i % columns;
            int ty = i / columns;

            int dstX = tx * cell + pad;
            int dstY = ty * cell + pad;

            blit(atlas, src, dstX, dstY);
            extrudePadding(atlas, dstX, dstY, tileSize, pad);

            String name = stripExt(p.getFileName().toString());
            map.append(name).append(" ").append(tx).append(" ").append(ty).append("\n");
        }

        Files.createDirectories(outPng.getParent());
        ImageIO.write(atlas, "PNG", outPng.toFile());

        Files.writeString(outMap, map.toString());
    }

    private static String stripExt(String s) {
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(0, dot) : s;
    }

    private static void blit(BufferedImage dst, BufferedImage src, int dx, int dy) {
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                dst.setRGB(dx + x, dy + y, src.getRGB(x, y));
            }
        }
    }

    /**
     * Fills padding around the tile area by copying edge pixels outward.
     * Tile area is [x..x+tileSize-1], [y..y+tileSize-1].
     */
    private static void extrudePadding(BufferedImage img, int x, int y, int tileSize, int pad) {
        int x0 = x, y0 = y;
        int x1 = x + tileSize - 1;
        int y1 = y + tileSize - 1;

        // left/right columns
        for (int py = 0; py < tileSize; py++) {
            int cL = img.getRGB(x0, y0 + py);
            int cR = img.getRGB(x1, y0 + py);
            for (int k = 1; k <= pad; k++) {
                img.setRGB(x0 - k, y0 + py, cL);
                img.setRGB(x1 + k, y0 + py, cR);
            }
        }

        // top/bottom rows (including padded columns)
        for (int px = -pad; px < tileSize + pad; px++) {
            int cT = img.getRGB(x0 + px, y0);
            int cB = img.getRGB(x0 + px, y1);
            for (int k = 1; k <= pad; k++) {
                img.setRGB(x0 + px, y0 - k, cT);
                img.setRGB(x0 + px, y1 + k, cB);
            }
        }

        // corners are covered by the row fill above (since it includes padded columns)
    }
    
    /**
     * Alpha-bleed (a.k.a. color dilation) for mipmap-safe cutout textures.
     *
     * Many PNG tiles have fully transparent pixels with RGB=(0,0,0). When OpenGL
     * builds mipmaps, it averages RGB regardless of alpha, which causes dark/black
     * fringes at distance/angles. This function "floods" RGB from opaque pixels
     * into transparent pixels while keeping alpha unchanged.
     *
     * @param src Input tile (TYPE_INT_ARGB recommended)
     * @param iterations How far to bleed. Use at least pad (e.g. pad=8 => iterations=8)
     * @param alphaThreshold Treat pixels with alpha <= threshold as "transparent".
     *                       0 works, but 1..8 can be more robust if your art has tiny alpha.
     */
    private static BufferedImage alphaBleed(BufferedImage src, int iterations, int alphaThreshold) {
        int w = src.getWidth();
        int h = src.getHeight();

        // Copy into int arrays for speed
        int[] cur = new int[w * h];
        int[] next = new int[w * h];
        src.getRGB(0, 0, w, h, cur, 0, w);
        System.arraycopy(cur, 0, next, 0, cur.length);

        // 8-neighborhood offsets
        final int[] ox = {-1, 0, 1, -1, 1, -1, 0, 1};
        final int[] oy = {-1,-1,-1,  0, 0,  1, 1, 1};

        for (int it = 0; it < iterations; it++) {
            // Start next as cur (so we only change pixels we decide to fill this iter)
            System.arraycopy(cur, 0, next, 0, cur.length);

            boolean changed = false;

            for (int y = 0; y < h; y++) {
                int row = y * w;
                for (int x = 0; x < w; x++) {
                    int idx = row + x;
                    int c = cur[idx];
                    int a = (c >>> 24) & 0xFF;

                    // Only fill pixels that are (near) transparent
                    if (a > alphaThreshold) continue;

                    // Find any neighboring pixel that is opaque enough
                    int best = -1;
                    int bestA = -1;

                    for (int n = 0; n < 8; n++) {
                        int xn = x + ox[n];
                        int yn = y + oy[n];
                        if (xn < 0 || xn >= w || yn < 0 || yn >= h) continue;

                        int nc = cur[yn * w + xn];
                        int na = (nc >>> 24) & 0xFF;
                        if (na > alphaThreshold && na > bestA) {
                            bestA = na;
                            best = nc;
                        }
                    }

                    if (best != -1) {
                        // Copy RGB from neighbor, keep original alpha (transparent)
                        int rgb = best & 0x00FFFFFF;
                        int out = (a << 24) | rgb;
                        next[idx] = out;
                        changed = true;
                    }
                }
            }

            // swap
            int[] tmp = cur;
            cur = next;
            next = tmp;

            // early-out if nothing changed this iteration
            if (!changed) break;
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, cur, 0, w);
        return out;
    }
}