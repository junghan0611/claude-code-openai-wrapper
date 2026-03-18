{
  description = "ccow — Claude Code OpenAI Wrapper (Clojure/GraalVM native)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        graalvm = pkgs.graalvmPackages.graalvm-ce;

        fhsEnv = pkgs.buildFHSEnv {
          name = "ccow-build";
          targetPkgs = pkgs: with pkgs; [
            clojure
            graalvm
            zlib
            glibc
            glibc.static
          ];
          runScript = pkgs.writeShellScript "ccow-build-init" ''
            export JAVA_HOME=${graalvm}
            export GRAALVM_HOME=${graalvm}
            exec bash "$@"
          '';
        };
      in
      {
        packages.fhs = fhsEnv;

        devShells = {
          default = pkgs.mkShell {
            name = "ccow";
            buildInputs = with pkgs; [ clojure graalvm ];
            JAVA_HOME = graalvm;
            GRAALVM_HOME = graalvm;
            shellHook = ''
              echo "ccow dev shell (GraalVM $(native-image --version 2>/dev/null | head -1))"
              echo "  ./run.sh start       — JVM으로 서버 시작"
              echo "  ./run.sh build       — native binary 빌드"
              echo "  ./run.sh test        — 테스트"
            '';
          };

          jvm = pkgs.mkShell {
            name = "ccow-jvm";
            buildInputs = with pkgs; [ clojure jdk17_headless ];
            shellHook = ''
              echo "ccow dev shell (JVM only)"
            '';
          };
        };
      });
}
