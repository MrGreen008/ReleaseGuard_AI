"""Seed only the repository-owned Flagsmith PostgreSQL service."""

import json
import os

from api_keys.models import MasterAPIKey
from django.contrib.auth import get_user_model
from django.db import transaction
from environments.models import Environment
from features.feature_types import MULTIVARIATE
from features.models import Feature, FeatureState
from features.multivariate.models import MultivariateFeatureOption, MultivariateFeatureStateValue
from organisations.models import Organisation, OrganisationRole
from projects.models import Project


@transaction.atomic
def seed():
    email = "releaseguard-admin@example.invalid"
    user_model = get_user_model()
    user = user_model.objects.filter(email=email).first()
    if user is None:
        user = user_model.objects.create_superuser(
            email=email, password=os.environ["FLAGSMITH_ADMIN_PASSWORD"]
        )
    organisation, _ = Organisation.objects.get_or_create(name="ReleaseGuard Local Lab")
    if not user.organisations.filter(pk=organisation.pk).exists():
        user.add_organisation(organisation, OrganisationRole.ADMIN)
    project, _ = Project.objects.get_or_create(
        name="ReleaseGuard AI", organisation=organisation
    )
    environment, _ = Environment.objects.get_or_create(
        name="releaseguard-lab",
        project=project,
        defaults={"use_v2_feature_versioning": False, "allow_client_traits": False, "hide_disabled_flags": False},
    )
    flag, _ = Feature.objects.get_or_create(
        project=project,
        name="new_payment_engine",
        defaults={"type": MULTIVARIATE, "initial_value": "stable", "default_enabled": True},
    )
    state = FeatureState.objects.get(
        feature=flag, environment=environment, feature_segment=None, identity=None
    )
    option, _ = MultivariateFeatureOption.objects.get_or_create(
        feature=flag,
        key="experimental",
        defaults={"type": "unicode", "string_value": "experimental", "default_percentage_allocation": 40},
    )
    MultivariateFeatureStateValue.objects.get_or_create(
        feature_state=state,
        multivariate_feature_option=option,
        defaults={"percentage_allocation": 40},
    )
    for candidate in ("fast_checkout", "inventory_v2", "smart_recommendations"):
        Feature.objects.get_or_create(project=project, name=candidate)
    _, token = MasterAPIKey.objects.create_key(
        name="ReleaseGuard local execution", organisation=organisation, created_by=user
    )
    return {
        "FLAGSMITH_ENVIRONMENT_KEY": environment.api_key,
        "FLAGSMITH_ENVIRONMENT_ID": environment.api_key,
        "FLAGSMITH_ALLOWED_ENVIRONMENT_ID": environment.api_key,
        "FLAGSMITH_MANAGEMENT_API_KEY": token,
        "FLAGSMITH_PROJECT_ID": project.id,
    }


print("RELEASEGUARD_CONFIG=" + json.dumps(seed()))
