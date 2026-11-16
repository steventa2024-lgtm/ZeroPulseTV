# R8 shrinks unused library code (mostly Guava pulled in by Media3). Names are kept so that stack traces
# from a TV stay readable and nothing that depends on a class name can break.
-dontobfuscate
