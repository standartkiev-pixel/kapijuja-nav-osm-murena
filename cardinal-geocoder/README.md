This code is derived from [Airmail](https://github.com/ellenhp/airmail). Its job is to let users search for POIs even if they don't have access to the internet. Like Airmail, this geocoder is built on top of the tantivy inverted index crate, and uses libpostal dictionaries for synonym substitution.

The geocoder is written in Rust and is called by the Kotlin project with the help of [uniffi](https://mozilla.github.io/uniffi-rs/latest/).

Most of the search code is in [index.rs](./src/index.rs) and the substitutions are in [substitutions.rs](./src/substitutions.rs).
