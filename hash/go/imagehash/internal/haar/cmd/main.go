package main

import (
	"fmt"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/haar"
)

func main() {
	x := [][]float64{
		{1, 2, 3, 4},
		{5, 6, 7, 8},
		{9, 10, 11, 12},
		{13, 14, 15, 16},
	}
	cA, cH, cV, cD := haar.Dwt2(x)
	fmt.Println("cA:", cA)
	fmt.Println("cH:", cH)
	fmt.Println("cV:", cV)
	fmt.Println("cD:", cD)
	rec := haar.Idwt2(cA, cH, cV, cD)
	fmt.Println("Idwt2 recon:", rec)

	// multi-level 2
	dec := haar.Wavedec2(x, 2)
	fmt.Println("dec.CA:", dec.CA)
	fmt.Println("len Details:", len(dec.Details))
	for i, d := range dec.Details {
		fmt.Printf("Details[%d]: cH=%v cV=%v cD=%v\n", i, d[0], d[1], d[2])
	}
	recm := haar.Waverec2(dec)
	fmt.Println("Waverec2:", recm)
}
