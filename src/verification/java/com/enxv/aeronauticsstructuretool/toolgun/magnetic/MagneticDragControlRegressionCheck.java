package com.enxv.aeronauticsstructuretool.toolgun.magnetic;

import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class MagneticDragControlRegressionCheck {
    private static final double EPSILON = 1.0E-8D;

    private MagneticDragControlRegressionCheck() {
    }

    public static void main(String[] args) {
        verifyConstraintTargetFrame();
        verifyStaffMotorTuning();
        verifySurvivalDampingDoesNotOvershoot();
        verifyUprightOrientation();
        verifyLaunchSpeedScaling();
    }

    private static void verifyConstraintTargetFrame() {
        Vector3d worldTarget = new Vector3d(20.0D, 8.0D, -6.0D);
        Quaterniond orientation = new Quaterniond().rotateXYZ(0.4D, -0.7D, 0.25D);
        Vector3d jointTarget = MagneticDragPhysics.jointLinearTarget(
                worldTarget,
                orientation,
                new Vector3d()
        );
        Vector3d reconstructedWorldTarget = orientation.transform(jointTarget, new Vector3d());
        require(reconstructedWorldTarget.distance(worldTarget) < EPSILON,
                "free-constraint linear target changed coordinate frames");
    }

    private static void verifyStaffMotorTuning() {
        MagneticDragPhysics.ConstraintTuning creative = MagneticDragPhysics.tuning(
                MagneticDragPhysics.Profile.CREATIVE
        );
        require(creative.linearStiffness() == 2650.0D, "creative linear stiffness differs from Physics Staff");
        require(creative.linearDamping() == 125.0D, "creative linear damping differs from Physics Staff");
        require(creative.angularStiffness() == 10_000.0D, "creative angular stiffness differs from Physics Staff");
        require(creative.angularDamping() == 850.0D, "creative angular damping differs from Physics Staff");
        require(creative.linearMaxForce() == 0.0D && creative.angularMaxForce() == 0.0D,
                "creative Physics Staff profile unexpectedly limits motor force");

        MagneticDragPhysics.ConstraintTuning standard = MagneticDragPhysics.tuning(
                MagneticDragPhysics.Profile.STANDARD
        );
        require(standard.linearStiffness() > 0.0D && standard.linearStiffness() < creative.linearStiffness(),
                "survival linear motor is not a reduced Physics Staff profile");
        require(standard.angularStiffness() > 0.0D && standard.angularStiffness() < creative.angularStiffness(),
                "survival angular motor is not a reduced Physics Staff profile");
        require(standard.linearMaxForce() == 5500.0D && standard.angularMaxForce() == 24_000.0D,
                "survival motor safety limits changed unexpectedly");
    }

    private static void verifySurvivalDampingDoesNotOvershoot() {
        MagneticDragPhysics.ConstraintTuning tuning = MagneticDragPhysics.tuning(
                MagneticDragPhysics.Profile.STANDARD
        );
        for (double mass : new double[]{1.0D, 3.0D, 10.0D, 25.0D, 50.0D, 100.0D}) {
            double damping = MagneticDragPhysics.linearDampingForMass(
                    mass,
                    MagneticDragPhysics.Profile.STANDARD
            );
            double position = 0.0D;
            double velocity = 0.0D;
            double target = 8.0D;
            double maximumPosition = position;
            double timeStep = 0.001D;
            for (int step = 0; step < 12_000; step++) {
                double force = tuning.linearStiffness() * (target - position) - damping * velocity;
                force = Math.max(-tuning.linearMaxForce(), Math.min(tuning.linearMaxForce(), force));
                velocity += force / mass * timeStep;
                position += velocity * timeStep;
                maximumPosition = Math.max(maximumPosition, position);
            }
            require(maximumPosition <= target + 1.0E-5D,
                    "survival constraint overshot its target for mass " + mass);
            require(Math.abs(target - position) < 1.0E-4D,
                    "survival constraint did not settle for mass " + mass);
        }
    }

    private static void verifyCameraRelativeOrientation() {
        Quaterniond cameraTurn = new Quaterniond().rotateY(0.7D).rotateX(-0.35D);
        Vector3d forward = cameraTurn.transform(new Vector3d(0.0D, 0.0D, -1.0D));
        Vector3d right = cameraTurn.transform(new Vector3d(1.0D, 0.0D, 0.0D));
        Vector3d up = cameraTurn.transform(new Vector3d(0.0D, 1.0D, 0.0D));
        Quaterniond reconstructedCamera = MagneticDragControlMath.cameraOrientation(
                forward,
                right,
                up,
                new Quaterniond()
        );
        require(sameRotation(cameraTurn, reconstructedCamera),
                "camera basis reconstruction changed orientation");

        Quaterniond initialObject = new Quaterniond().rotateZ(0.25D);
        Quaterniond followedObject = MagneticDragControlMath.applyReferenceFrameDelta(
                new Quaterniond(),
                reconstructedCamera,
                initialObject,
                new Quaterniond()
        );
        Quaterniond expected = new Quaterniond(reconstructedCamera).mul(initialObject).normalize();
        require(sameRotation(followedObject, expected),
                "object did not preserve its camera-relative orientation");
    }

    private static void verifyUprightOrientation() {
        Quaterniond captured = new Quaterniond()
                .rotateY(0.7D)
                .rotateX(-0.35D)
                .rotateZ(0.25D);
        Quaterniond upright = MagneticDragControlMath.uprightOrientation(
                captured,
                new Quaterniond()
        );
        Vector3d localY = upright.transform(new Vector3d(0.0D, 1.0D, 0.0D));
        require(localY.distance(new Vector3d(0.0D, 1.0D, 0.0D)) < EPSILON,
                "automatic magnetic-gun alignment did not make local Y world-up");

        Quaterniond expectedTwist = new Quaterniond(
                0.0D,
                captured.y,
                0.0D,
                captured.w
        ).normalize();
        require(sameRotation(upright, expectedTwist),
                "automatic alignment changed the object's world-Y heading");

        Quaterniond manual = new Quaterniond().rotateXYZ(0.31D, -0.42D, 0.18D);
        Quaterniond target = upright.mul(manual, new Quaterniond()).normalize();
        require(!sameRotation(target, upright),
                "manual Tab rotation was lost when composing the upright frame");
        Quaterniond recomposed = upright.mul(manual, new Quaterniond()).normalize();
        require(sameRotation(target, recomposed),
                "manual rotation was not stable across target recomposition");

        Quaterniond singular = new Quaterniond().rotateY(0.9D).rotateX(Math.PI);
        Quaterniond singularUpright = MagneticDragControlMath.uprightOrientation(
                singular,
                new Quaterniond()
        );
        Vector3d singularY = singularUpright.transform(new Vector3d(0.0D, 1.0D, 0.0D));
        require(singularY.distance(new Vector3d(0.0D, 1.0D, 0.0D)) < EPSILON,
                "singular upright orientation did not remain world-up");

        Quaterniond cameraTurned = new Quaterniond().rotateY(0.55D)
                .mul(upright, new Quaterniond()).normalize();
        Vector3d cameraTurnedY = cameraTurned.transform(new Vector3d(0.0D, 1.0D, 0.0D));
        require(cameraTurnedY.distance(new Vector3d(0.0D, 1.0D, 0.0D)) < EPSILON,
                "camera yaw follow tilted the upright Y axis");
        require(!sameRotation(cameraTurned, upright),
                "camera yaw follow did not change the XZ heading");
    }

    private static void verifyLaunchSpeedScaling() {
        double smallSpeed = MagneticDragPhysics.launchSpeedForMass(
                3.0D,
                MagneticDragPhysics.Profile.STANDARD
        );
        double heavySpeed = MagneticDragPhysics.launchSpeedForMass(
                100.0D,
                MagneticDragPhysics.Profile.STANDARD
        );
        require(Math.abs(smallSpeed - 42.0D) < EPSILON,
                "small structures no longer receive projectile launch speed");
        require(heavySpeed >= 6.0D && heavySpeed < smallSpeed,
                "launch speed no longer scales down for heavy structures");
    }

    private static boolean sameRotation(Quaterniond first, Quaterniond second) {
        return Math.abs(Math.abs(first.dot(second)) - 1.0D) < EPSILON;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
