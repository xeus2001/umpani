package main

import (
	"fmt"
	"net/http"
	"path/filepath"
)

func main() {
	relativePath := "./web"
	absolutePath, err := filepath.Abs(relativePath)
	if err != nil {
		fmt.Printf("Failed to resolve absolute absolutePath of %s", relativePath)
	}
	dir := http.Dir(absolutePath)
	fmt.Printf("Serving directory: %s\n", absolutePath)
	fileServer := http.FileServer(dir)
	if err := http.ListenAndServe(":8080", fileServer); err != nil {
		fmt.Printf("Error starting the server: %s", err)
	}
}
