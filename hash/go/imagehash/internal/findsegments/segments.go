// Package findsegments implements imagehash._find_all_segments, finding connected
// regions in a float32 grayscale pixel array using a two-pass flood fill.
//
// Algorithm:
//  1. Find "hills" (pixels > threshold) by scanning row-major and BFS-expanding
//     each unvisited hill pixel into a connected region.
//  2. Find "valleys" (pixels <= threshold) similarly.
//  3. Keep regions larger than minSegmentSize pixels.
//  4. Return segments in the order found (hills first, then valleys).
//
// Flood fill uses 4-connectivity. Starting pixel for each region is the first
// unvisited qualifying pixel in row-major (y-first) order, matching numpy.nonzero.
//
// Termination of valley pass mirrors Python's
//   while len(already_segmented) < img_width * img_height
// where img_width = H and img_height = W (from pixels.shape), and
// already_segmented starts with 2*(H+W) border pixels.
package findsegments

// Pixel is a (y, x) coordinate pair.
type Pixel struct{ Y, X int }

// Segment is a set of (y, x) coordinates belonging to a connected region.
type Segment []Pixel

// FindAllSegments partitions a float32 grayscale image into connected regions
// of hills (pixels > threshold) and valleys (pixels <= threshold), returning
// only regions with more than minSegmentSize pixels.
//
// pixels is indexed [y][x]. Returns segments in order found (hills first).
func FindAllSegments(pixels [][]float32, threshold float32, minSegmentSize int) []Segment {
	h := len(pixels)
	if h == 0 {
		return nil
	}
	w := len(pixels[0])
	if w == 0 {
		return nil
	}

	visited := make([]bool, h*w) // visited[y*w+x]
	var segments []Segment

	// assignedCount mirrors Python's len(already_segmented):
	// starts at 2*(H+W) for the border pixels outside the image.
	assignedCount := 2 * (h + w)

	// Find first unvisited pixel satisfying predicate, in row-major order.
	findFirst := func(pred func(float32) bool) (Pixel, bool) {
		for y := 0; y < h; y++ {
			for x := 0; x < w; x++ {
				if !visited[y*w+x] && pred(pixels[y][x]) {
					return Pixel{y, x}, true
				}
			}
		}
		return Pixel{}, false
	}

	// BFS flood fill from start, gathering all connected pixels that satisfy pred.
	// Adds found pixels to visited. Returns the region.
	floodFill := func(start Pixel, pred func(float32) bool) Segment {
		var region Segment
		queue := []Pixel{start}
		visited[start.Y*w+start.X] = true
		for len(queue) > 0 {
			p := queue[0]
			queue = queue[1:]
			region = append(region, p)
			// 4-neighbors
			neighbors := [4]Pixel{
				{p.Y - 1, p.X},
				{p.Y + 1, p.X},
				{p.Y, p.X - 1},
				{p.Y, p.X + 1},
			}
			for _, n := range neighbors {
				if n.Y < 0 || n.Y >= h || n.X < 0 || n.X >= w {
					continue
				}
				if visited[n.Y*w+n.X] {
					continue
				}
				if !pred(pixels[n.Y][n.X]) {
					continue
				}
				visited[n.Y*w+n.X] = true
				queue = append(queue, n)
			}
		}
		return region
	}

	// Pass 1: hills (pixels > threshold)
	hillPred := func(v float32) bool { return v > threshold }
	for {
		start, ok := findFirst(hillPred)
		if !ok {
			break
		}
		region := floodFill(start, hillPred)
		assignedCount += len(region)
		if len(region) > minSegmentSize {
			segments = append(segments, region)
		}
	}

	// Pass 2: valleys (pixels <= threshold)
	// Mirrors Python: while len(already_segmented) < img_width * img_height
	// where img_width*img_height = H*W
	valleyPred := func(v float32) bool { return v <= threshold }
	for assignedCount < h*w {
		start, ok := findFirst(valleyPred)
		if !ok {
			break
		}
		region := floodFill(start, valleyPred)
		assignedCount += len(region)
		if len(region) > minSegmentSize {
			segments = append(segments, region)
		}
	}

	return segments
}
