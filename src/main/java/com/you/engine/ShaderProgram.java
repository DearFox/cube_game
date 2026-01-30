package com.you.engine;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;

public class ShaderProgram {

    private final int programId;
    private final Map<String, Integer> uniformCache = new HashMap<>();

    private ShaderProgram(int programId) {
        this.programId = programId;
    }

    public static ShaderProgram loadFromResources(String vertPath, String fragPath) {
        String vertSrc = readResource(vertPath);
        String fragSrc = readResource(fragPath);

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vertSrc);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Vertex shader compile error:\n" + glGetShaderInfoLog(vs));
        }

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fragSrc);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Fragment shader compile error:\n" + glGetShaderInfoLog(fs));
        }

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Program link error:\n" + glGetProgramInfoLog(prog));
        }

        glDeleteShader(vs);
        glDeleteShader(fs);

        return new ShaderProgram(prog);
    }

    private static String readResource(String path) {
        try (InputStream is = ShaderProgram.class.getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Resource not found: " + path);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                return sb.toString();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }

    public void use() {
        glUseProgram(programId);
    }

    public void delete() {
        glDeleteProgram(programId);
    }

    private int u(String name) {
        return uniformCache.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public void setInt(String name, int v) {
        glUniform1i(u(name), v);
    }

    public void setVec3(String name, float x, float y, float z) {
        glUniform3f(u(name), x, y, z);
    }

    public void setMat4(String name, Matrix4f m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            glUniformMatrix4fv(u(name), false, m.get(fb));
        }
    }
    
    public void useNone() { glUseProgram(0); }
    
    public void setVec2(String name, float x, float y) {
        int loc = glGetUniformLocation(programId, name);
        if (loc >= 0) {
            glUniform2f(loc, x, y);
        }
    }

}

