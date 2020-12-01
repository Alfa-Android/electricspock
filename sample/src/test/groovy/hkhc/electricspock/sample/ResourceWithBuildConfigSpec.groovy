package hkhc.electricspock.sample

import android.content.Context
import hkhc.electricspock.ElectricSpecification
import org.robolectric.RuntimeEnvironment

/**
 * Created by hermanc on 2/8/2017.
 */
class ResourceWithBuildConfigSpec extends ElectricSpecification {

    def "Test cases shall be able to access Android resources with BuildConfig"() {
        given:
            Context context = RuntimeEnvironment.application;
        when: "Access resource"
            String appName = context.getResources().getString(R.string.app_name);
        then:
            appName == "ElectricSpock"
    }

}