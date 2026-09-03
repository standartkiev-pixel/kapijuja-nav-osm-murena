// This is run by the CI/CD job to create a Kotlin file definining the FFI interface of this crate, allowing it to be used from Kotlin.
fn main() {
    uniffi::uniffi_bindgen_main()
}
