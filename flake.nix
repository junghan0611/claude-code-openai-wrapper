{
  description = "Claude Code OpenAI Wrapper - Doom Emacs/gptel integration";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        python = pkgs.python312;
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            python
            pkgs.poetry
            pkgs.git
            # For building Python packages with native dependencies
            pkgs.gcc
            pkgs.stdenv.cc.cc.lib
          ];

          shellHook = ''
            echo "🐍 Claude Code OpenAI Wrapper Development Shell"
            echo "   Python: $(python --version)"
            echo "   Poetry: $(poetry --version)"
            echo ""
            echo "📦 Quick Start:"
            echo "   poetry install        # Install dependencies"
            echo "   poetry run uvicorn src.main:app --reload  # Start dev server"
            echo ""
            echo "🧪 Testing:"
            echo "   poetry run pytest     # Run tests"
            echo ""

            # Set up Poetry to use local virtualenv
            export POETRY_VIRTUALENVS_IN_PROJECT=true

            # Install dependencies if pyproject.toml exists and .venv doesn't
            if [ -f pyproject.toml ] && [ ! -d .venv ]; then
              echo "📥 Installing dependencies..."
              poetry install --no-interaction
            fi
          '';

          # Ensure Python packages can find shared libraries
          LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [
            pkgs.stdenv.cc.cc.lib
          ];
        };
      }
    );
}
