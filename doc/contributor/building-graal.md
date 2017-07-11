# Building Graal

If you want to build Graal from scratch to test running with Graal when
developing TruffleRuby, or possibly to modify Graal or Truffle, or to use
developer tools that come with Graal such as the Ideal Graph Visualizer, then
you should install Graal locally.

Our workflow tool can install Graal automatically under the directory `graal`
with:

```
$ jt install graal
```

You can then set the `GRAAL_HOME` environment variable to the location of the
`compiler` directory inside the `graal` repository and use `jt` to run
TruffleRuby.

```
$ GRAAL_HOME=$PWD/graal/graal/compiler jt ruby --graal ...
```

If you want to build Graal by yourself, follow the instructions in the Graal
repository.

https://github.com/graalvm/graal
