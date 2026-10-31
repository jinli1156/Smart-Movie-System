package com.movie.ticket.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.movie.ticket.exception.BusinessException;

import java.util.LinkedHashSet;
import java.util.Set;

/** Aisles are boundaries after seat columns; 0 and cols represent the outer edges. */
public record HallSeatLayout(boolean[][] enabled, Set<Integer> aisleAfterCols) {
    public static HallSeatLayout parse(String json, int rows, int cols) {
        if (rows < 1 || rows > 50 || cols < 1 || cols > 50) {
            throw new BusinessException("影厅行列数须为 1-50");
        }
        try {
            Object value = json == null || json.isBlank() ? null : JSON.parse(json);
            JSONArray seats;
            Set<Integer> aisles = new LinkedHashSet<>();
            if (value instanceof JSONObject object) {
                seats = object.getJSONArray("seats");
                if (seats == null) throw new IllegalArgumentException();
                JSONArray boundaries = object.getJSONArray("aisleAfterCols");
                if (boundaries == null) throw new IllegalArgumentException();
                for (Object boundary : boundaries) {
                    if (!(boundary instanceof Integer col) || col < 0 || col > cols || !aisles.add(col)) {
                        throw new IllegalArgumentException();
                    }
                }
            } else {
                // Legacy pages displayed a central aisle. Preserve it explicitly when editing.
                if (value != null && !(value instanceof JSONArray)) throw new IllegalArgumentException();
                seats = (JSONArray) value;
                if (cols > 1) aisles.add(cols / 2);
            }
            boolean[][] enabled = new boolean[rows][cols];
            if (seats != null && seats.size() != rows) throw new IllegalArgumentException();
            for (int r = 0; r < rows; r++) {
                JSONArray row = seats == null ? null : seats.getJSONArray(r);
                if (seats != null && (row == null || row.size() != cols)) throw new IllegalArgumentException();
                for (int c = 0; c < cols; c++) {
                    Object cell = row == null ? 1 : row.get(c);
                    if (!(cell instanceof Integer n) || (n != 0 && n != 1)) throw new IllegalArgumentException();
                    enabled[r][c] = (Integer) cell == 1;
                }
            }
            return new HallSeatLayout(enabled, Set.copyOf(aisles));
        } catch (RuntimeException exception) {
            throw new BusinessException("座位布局或过道配置无效，请检查行列数和过道位置");
        }
    }
}
