//! Pixel-level segmentation matching Python `imagehash._find_all_segments`.
//!
//! Input: 2-D `Vec<Vec<f32>>` pixels, shape (H, W).
//! Output: `Vec<Vec<(usize, usize)>>` — list of segments in order of discovery;
//! each segment is sorted row-major (y, x).
//!
//! Two passes:
//!   1. Hills: pixels > threshold  (discovered in row-major order of start pixel)
//!   2. Valleys: pixels <= threshold (same)
//!
//! Only segments with `len > min_size` are kept.
//!
//! Connectivity: 4-neighbor (up, down, left, right) — matching Python.
//!
//! Border semantics: matches Python `_find_all_segments` exactly.
//! The valley loop terminates when `border_count + assigned_count >= H * W`,
//! where `border_count = 2*(H+W)`.  For small images this means the valley
//! loop may never execute at all.

use std::collections::VecDeque;

/// BFS flood-fill matching Python `imagehash._find_region`.
///
/// Differences from a naive BFS:
/// - `globally_assigned` tracks pixels claimed by ANY segment (global).
/// - `locally_seen` tracks pixels rejected THIS BFS call (local, matching
///   Python's `not_in_region` set that prevents re-trying within one call).
/// - Non-candidate neighbors are recorded in `locally_seen` but NOT in
///   `globally_assigned`, matching Python's semantics exactly.
fn find_region(
    is_candidate: &[Vec<bool>],
    globally_assigned: &mut Vec<Vec<bool>>,
    start: (usize, usize),
    h: usize,
    w: usize,
) -> Vec<(usize, usize)> {
    // locally_seen tracks pixels we've tried this BFS (both accepted and rejected)
    let mut locally_seen = vec![vec![false; w]; h];
    let mut in_region: Vec<(usize, usize)> = Vec::new();
    let mut queue: VecDeque<(usize, usize)> = VecDeque::new();

    in_region.push(start);
    globally_assigned[start.0][start.1] = true;
    locally_seen[start.0][start.1] = true;
    queue.push_back(start);

    while let Some((y, x)) = queue.pop_front() {
        let neighbors: [(i64, i64); 4] = [
            (y as i64 - 1, x as i64),
            (y as i64 + 1, x as i64),
            (y as i64, x as i64 - 1),
            (y as i64, x as i64 + 1),
        ];
        for (ny, nx) in neighbors {
            if ny < 0 || nx < 0 || ny >= h as i64 || nx >= w as i64 {
                continue;
            }
            let (ny, nx) = (ny as usize, nx as usize);
            // Skip if globally assigned OR seen locally this BFS
            if globally_assigned[ny][nx] || locally_seen[ny][nx] {
                continue;
            }
            locally_seen[ny][nx] = true;
            if is_candidate[ny][nx] {
                globally_assigned[ny][nx] = true;
                in_region.push((ny, nx));
                queue.push_back((ny, nx));
            }
            // else: recorded in locally_seen (= Python's not_in_region)
        }
    }

    in_region.sort_unstable();
    in_region
}

/// Find all segments, matching Python `imagehash._find_all_segments` exactly.
///
/// Returns segments in discovery order:
/// - Hills first (row-major scan order of starting pixels)
/// - Valleys after (same)
///
/// Each segment is sorted by `(row, col)` — matching `sorted(segment_set)` in Python.
pub fn find_all_segments(
    pixels: &[Vec<f32>],
    threshold: f32,
    min_size: usize,
) -> Vec<Vec<(usize, usize)>> {
    let h = pixels.len();
    if h == 0 {
        return vec![];
    }
    let w = pixels[0].len();
    if w == 0 {
        return vec![];
    }

    let is_hill: Vec<Vec<bool>> =
        pixels.iter().map(|row| row.iter().map(|&v| v > threshold).collect()).collect();
    let is_valley: Vec<Vec<bool>> =
        pixels.iter().map(|row| row.iter().map(|&v| v <= threshold).collect()).collect();

    let mut globally_assigned = vec![vec![false; w]; h];
    let mut segments: Vec<Vec<(usize, usize)>> = Vec::new();

    // --- Hills pass ---
    loop {
        let start = (0..h)
            .flat_map(|y| (0..w).map(move |x| (y, x)))
            .find(|&(y, x)| is_hill[y][x] && !globally_assigned[y][x]);
        let Some(start) = start else { break };
        let seg = find_region(&is_hill, &mut globally_assigned, start, h, w);
        if seg.len() > min_size {
            segments.push(seg);
        }
    }

    // --- Valley pass ---
    // Python's termination condition: `len(already_segmented) < H*W`
    // where already_segmented = {border sentinel pixels} ∪ {assigned interior pixels}.
    // Border sentinels: (-1,z) for z∈[0,W), (H,z) for z∈[0,W),
    //                   (z,-1) for z∈[0,H), (z,W)  for z∈[0,H).
    // Total border sentinels = 2*(H + W).
    // Python's condition becomes: 2*(H+W) + assigned_count < H*W
    // i.e., the valley loop only runs if assigned_count < H*W - 2*(H+W).
    let border_count = 2 * (h + w);

    loop {
        let assigned_count: usize = globally_assigned
            .iter()
            .flat_map(|row| row.iter())
            .filter(|&&v| v)
            .count();
        if border_count + assigned_count >= h * w {
            break;
        }

        let start = (0..h)
            .flat_map(|y| (0..w).map(move |x| (y, x)))
            .find(|&(y, x)| is_valley[y][x] && !globally_assigned[y][x]);
        let Some(start) = start else { break };
        let seg = find_region(&is_valley, &mut globally_assigned, start, h, w);
        if seg.len() > min_size {
            segments.push(seg);
        }
    }

    segments
}
