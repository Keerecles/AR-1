.PHONY: build clean run

all: run

build: build/src/dk/cs/au/skeen/AR/desktop/DesktopLauncher.class

# Find all the java source files
SOURCES := $(shell find src/ -name '*.java')
# Build all the class files, in particular the Launcher
build/src/dk/cs/au/skeen/AR/desktop/DesktopLauncher.class:
	mkdir -p build
	javac $(SOURCES) -d build/ -cp "jar/*"

# run the DesktopLauncher
run: build
	cd build && java -cp ".:../jar/*" -Djava.library.path=../libs/ dk/cs/au/skeen/AR/desktop/DesktopLauncher

# Clean the build directory
clean:
	rm -rf build/

