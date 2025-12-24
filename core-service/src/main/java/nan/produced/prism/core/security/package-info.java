@org.springframework.modulith.NamedInterface("api")
@org.springframework.modulith.ApplicationModule(
        displayName = "Security",
        allowedDependencies = {
                "common::response",
                "common::exception",
                "common::utils",
                "integration::integration-auth-signature"
        }
)
package nan.produced.prism.core.security;
