{
  description = "Gui4s development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-25.11-darwin";
    flake-utils.url = "github:numtide/flake-utils";
    mill-flake.url = "path:/Users/katze/nix/mill-flake";
  };

  outputs = { self, nixpkgs, flake-utils, mill-flake }:
    flake-utils.lib.eachSystem [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ] (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = { allowUnfree = true; };
        };
        mill = mill-flake.lib.${system}.mkMillFromFile ./.mill-version "sha256-3oNWgTuobsMQNV5dUZlC7khhwLQwVXpBOc2uyVWC2Q0=";
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            mill
            pkgs.jdk21
          ];
        };
      }
    );
}
