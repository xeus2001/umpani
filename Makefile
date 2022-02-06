GOBIN := $(shell pwd)/bin
CLIENT_SRC := $(shell find . -type f -name '*.go' -path "./pkg/umpani/client/*")
#VERSION := $(shell cat pkg/f3/version.go |grep "const Version ="|cut -d"\"" -f2)
#LIBNAME := libf3
CLIENT_NAME := umpani

# There is a default rule treating mod files as Modula files. This disables this rule:
%.o : %.mod

#docker: FLAGS := -ldflags "-X github.com/xeus2001/interview-accountapi/pkg/f3.DefaultEndPoint=http://accountapi:8080/v1"
#release: FLAGS := -ldflags "-X github.com/xeus2001/interview-accountapi/pkg/f3.DefaultEndPoint=https://api.f3.tech/v1"

#.PHONY: build-client
#build: build-client
#docker: do-build
#release: check do-build

.PHONY: build-client
build-client: FLAGS := -ldflags "-X github.com/xeus2001/interview-accountapi/pkg/f3.DefaultEndPoint=http://localhost:8080/v1"
build-client: get
	@echo "GOPATH: $(GOPATH)"
	@echo "LDFLAGS: $(FLAGS)"
	@echo "FILES: $(SRC)"
	#@GOPATH=$(GOPATH) GOBIN=$(GOBIN) go build $(FLAGS) -o bin/$(LIBNAME) cmd/main.go
	#@GOPATH=$(GOPATH) GOBIN=$(GOBIN) GOOS=linux GOARCH=amd64 go build $(FLAGS) -o bin/$(EXENAME) cmd/main.go
	@GOPATH=$(GOPATH) GOBIN=$(GOBIN) GOOS=js GOARCH=wasm go build -o web/$(CLIENT_NAME).wasm pkg/umpani/client/main.go
	#@GOPATH=$(GOPATH) GOBIN=$(GOBIN) GOOS=linux GOARCH=amd64 go build -o bin/$(CLIENT_NAME) pkg/umpani/client/main.go
	#@GOPATH=$(GOPATH) GOBIN=$(GOBIN) GOOS=windows GOARCH=amd64 go build -o bin/$(CLIENT_NAME).exe pkg/umpani/client/main.go
# To debug linked: LD_DEBUG=all

# Search at https://packages.ubuntu.com/impish/
#           https://packages.ubuntu.com/search?suite=impish&section=all&arch=any&searchon=contents&keywords=Xrandr.h
.PHONY: ubuntu-setup
ubuntu-setup:
	@sudo apt install -y libglx-dev libxcursor-dev libxrandr-dev libxinerama-dev libgl1-mesa-dev xorg-dev

.PHONY: doc
doc: fmt
	gomarkdoc --output doc/f3.md pkg/f3/*.go

.PHONY: swagger-ui
swagger-ui:
	chromium-browser \
      --disable-web-security \
      --user-data-dir="/tmp/chromium-debug/" \
      'http://localhost:7080/#/Health/get_health' 'http://localhost:7080/#/Health/get_health'

.PHONY: fmt
fmt:
	@echo $(SRC)
	gofmt -w $(SRC)

.PHONY: check
check:
	@sh -c "'$(CURDIR)/scripts/fmtcheck.sh'"

.PHONY: get
get:
	@GOPATH=$(GOPATH) GOBIN=$(GOBIN) go get github.com/hajimehoshi/ebiten/v2

.PHONY: clean
clean:
	@rm -f bin/$(LIBNAME)
	@rm -f bin/$(EXENAME)
	@rm -f bin/$(EXENAME).exe
	@rm -f coverage.out
	@docker image rm f3.int.test:latest 2>/dev/null || true

.PHONY: simplify
simplify:
	@gofmt -s -l -w $(SRC)

.PHONY: test
test:
	@GOPATH=$(GOPATH) GOBIN=$(GOBIN) go test -cover -v github.com/xeus2001/interview-accountapi/pkg/f3

.PHONY: test-int
test-int:
	@GOPATH=$(GOPATH) GOBIN=$(GOBIN) go test -cover -coverprofile=coverage.out -v -tags=int github.com/xeus2001/interview-accountapi/pkg/f3 -f3.endpoint=http://localhost:8080/v1

.PHONY: test-int-result
test-int-result:
	@go tool cover -html=coverage.out

.PHONY: test-docker
test-docker:
	@GOPATH=$(GOPATH) GOBIN=$(GOBIN) go test -cover -v -tags=int github.com/xeus2001/interview-accountapi/pkg/f3 -f3.endpoint=http://accountapi:8080/v1
