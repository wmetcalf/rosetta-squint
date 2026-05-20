package io.rosetta.imagehash.group1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rosetta.imagehash.internal.HaarDwt;
import io.rosetta.imagehash.testkit.SpecPath;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaarDwtTest {
    private static final double TOL = 1e-12;

    @Test
    void singleLevelMatchesPywt() throws IOException {
        JsonNode root = new ObjectMapper().readTree(SpecPath.HAAR_CASES.toFile());
        double[][] input = json2d(root.get("input"));
        JsonNode sl = root.get("single_level");
        double[][] expectedCA = json2d(sl.get("cA"));
        double[][] expectedCH = json2d(sl.get("cH"));
        double[][] expectedCV = json2d(sl.get("cV"));
        double[][] expectedCD = json2d(sl.get("cD"));

        HaarDwt.Dwt2Result r = HaarDwt.dwt2(input);
        assertArrayClose(expectedCA, r.cA);
        assertArrayClose(expectedCH, r.cH);
        assertArrayClose(expectedCV, r.cV);
        assertArrayClose(expectedCD, r.cD);
    }

    @Test
    void multiLevelLLAndReconstruction() throws IOException {
        JsonNode root = new ObjectMapper().readTree(SpecPath.HAAR_CASES.toFile());
        double[][] input = json2d(root.get("input"));
        JsonNode ml = root.get("multi_level_4");
        double[][] expectedCA = json2d(ml.get("cA"));
        double[][] expectedReconstructed = json2d(ml.get("reconstructed"));

        HaarDwt.WavedecResult dec = HaarDwt.wavedec2(input, 4);
        assertEquals(1, dec.cA.length, "deepest LL row count");
        assertEquals(1, dec.cA[0].length, "deepest LL col count");
        assertArrayClose(expectedCA, dec.cA);

        double[][] recon = HaarDwt.waverec2(dec);
        assertArrayClose(expectedReconstructed, recon);
        assertArrayClose(input, recon);
    }

    @Test
    void zeroLLOfFullDecompRemovesDC() {
        double[][] x = new double[4][4];
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                x[i][j] = 7.5;
        HaarDwt.WavedecResult dec = HaarDwt.wavedec2(x, 2);
        dec.cA[0][0] = 0;
        double[][] recon = HaarDwt.waverec2(dec);
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                assertTrue(Math.abs(recon[i][j]) < TOL, "expected 0 at (" + i + "," + j + "), got " + recon[i][j]);
    }

    private static double[][] json2d(JsonNode arr) {
        int h = arr.size();
        int w = arr.get(0).size();
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out[y][x] = arr.get(y).get(x).asDouble();
        return out;
    }

    private static void assertArrayClose(double[][] expected, double[][] actual) {
        assertEquals(expected.length, actual.length, "row count");
        for (int y = 0; y < expected.length; y++) {
            assertEquals(expected[y].length, actual[y].length, "col count at row " + y);
            for (int x = 0; x < expected[y].length; x++) {
                double diff = Math.abs(expected[y][x] - actual[y][x]);
                int yy = y, xx = x;
                assertTrue(diff < TOL, () -> "diff " + diff + " at (" + yy + "," + xx + "): expected " + expected[yy][xx] + ", got " + actual[yy][xx]);
            }
        }
    }
}
